package app.lull.playback.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * One hidden, offline WebView used as a JavaScript engine for two jobs YouTube now requires:
 *  - running BotGuard to mint proof-of-origin tokens (helpers in assets/po_token.html, from NewPipe);
 *  - solving the `sig` and `n` challenges in stream URLs with yt-dlp's solver (assets/ejs).
 *
 * The WebView never loads anything from the network itself; Lull's own HTTP client fetches the
 * BotGuard challenge and the player script and hands them in.
 *
 * Every public method blocks, so call them only from a background thread.
 */
class WebEngine(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val ids = AtomicLong()
    @Volatile private var webView: WebView? = null
    @Volatile private var ready: CompletableFuture<Unit>? = null

    /** Large inputs (the player script) are pulled by JavaScript instead of being inlined. */
    @Volatile private var playerSource: String? = null

    /** Evaluates [expression] (a value or a Promise) and returns it as a string (objects as JSON). */
    fun eval(expression: String, timeoutSeconds: Long = 15): String {
        check(Looper.myLooper() != Looper.getMainLooper()) { "WebEngine called on the main thread" }
        ensureReady()
        val id = ids.incrementAndGet().toString()
        val result = CompletableFuture<String>()
        pending[id] = result
        val script = """
            (async function () {
              try {
                const v = await ($expression);
                $BRIDGE.done("$id", true, typeof v === "string" ? v : JSON.stringify(v));
              } catch (e) {
                $BRIDGE.done("$id", false, String(e) + "\n" + (e && e.stack));
              }
            })();
        """.trimIndent()
        main.post { webView?.evaluateJavascript(script, null) ?: result.completeExceptionally(IOException("WebView is gone")) }
        try {
            return await(result, timeoutSeconds)
        } finally {
            pending.remove(id)
        }
    }

    /** Makes [source] available to JavaScript as `Lull.player()` for the next [eval]. */
    fun providePlayer(source: String) {
        playerSource = source
    }

    fun releasePlayer() {
        playerSource = null
    }

    /** Drops the WebView (for example after a broken state); the next call starts a new one. */
    @Synchronized
    fun reset() {
        ready = null
        pending.values.forEach { it.completeExceptionally(IOException("WebView was reset")) }
        pending.clear()
        main.post {
            webView?.apply {
                loadUrl("about:blank")
                removeJavascriptInterface(BRIDGE)
                destroy()
            }
            webView = null
        }
    }

    @Synchronized
    private fun ensureReady() {
        val current = ready
        if (current != null && !current.isCompletedExceptionally) {
            await(current, 20)
            return
        }
        val starting = CompletableFuture<Unit>()
        ready = starting
        main.post { create(starting) }
        try {
            await(starting, 20)
        } catch (e: IOException) {
            ready = null
            throw e
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun create(done: CompletableFuture<Unit>) {
        try {
            val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            val solver = listOf("ejs/lib.min.js", "ejs/core.min.js").map { name ->
                context.assets.open(name).bufferedReader().use { it.readText() }
            }
            val view = WebView(context.applicationContext)
            view.settings.apply {
                javaScriptEnabled = true
                blockNetworkLoads = true // the page needs no network; Lull fetches what it needs
                userAgentString = USER_AGENT
                safeBrowsingEnabled = false
            }
            view.addJavascriptInterface(Bridge(), BRIDGE)
            view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.w(TAG, "JS: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    }
                    return true
                }
            }
            view.webViewClient = object : WebViewClient() {
                // The page must never navigate: that would drop the tokens and the solver.
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = true

                override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    if (done.isDone && webView === v) lost(v, "page navigated to $url")
                }
                // Without this the whole app would crash if Android kills the WebView's renderer.
                override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (webView === v) lost(v, "renderer gone (crashed=${detail.didCrash()})")
                    v.destroy()
                    return true
                }

                override fun onPageFinished(v: WebView, url: String?) {
                    if (done.isDone) return
                    // yt-dlp's solver: lib defines `lib` (meriyah + astring), core defines `jsc`.
                    v.evaluateJavascript(solver[0] + "\n;var meriyah = lib.meriyah, astring = lib.astring;\n") {
                        v.evaluateJavascript(browserSafe(solver[1]) + "\n;typeof jsc") { type ->
                            if (type == "\"function\"") done.complete(Unit)
                            else done.completeExceptionally(IOException("Solver didn't load ($type)"))
                        }
                    }
                }
            }
            webView = view
            view.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        } catch (t: Throwable) {
            // No WebView installed or it failed to start; callers fall back to the old path.
            done.completeExceptionally(IOException("WebView unavailable: ${t.message}", t))
        }
    }

    /** The page's state is gone: fail what's waiting now (not after a timeout) and start fresh next time. */
    private fun lost(v: WebView, why: String) {
        Log.w(TAG, "WebView lost: $why")
        if (webView !== v) return
        webView = null
        ready = null
        val error = IOException("WebView lost: $why")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        stateLost = true
    }

    /** True once after the page state was lost, so callers know their tokens are gone too. */
    @Volatile var stateLost = false

    private inner class Bridge {
        @JavascriptInterface
        fun done(id: String, ok: Boolean, value: String) {
            val future = pending[id] ?: return
            if (ok) future.complete(value) else future.completeExceptionally(IOException("JS: $value"))
        }

        @JavascriptInterface
        fun player(): String = playerSource ?: ""
    }

    private fun <T> await(future: CompletableFuture<T>, seconds: Long): T = try {
        future.get(seconds, TimeUnit.SECONDS)
    } catch (e: ExecutionException) {
        throw (e.cause as? IOException) ?: IOException(e.cause)
    } catch (e: TimeoutException) {
        throw IOException("WebView timed out", e)
    }

    companion object {
        private const val TAG = "LullWebEngine"
        private const val BRIDGE = "Lull"

        /** The same desktop Chrome identity is used for BotGuard and for the stream requests. */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

        private const val LOCATION_ASSIGNMENT =
            "globalThis.location = new URL(\"https://www.youtube.com/watch?v=yt-dlp-wins\");"

        /**
         * yt-dlp's solver is made for Node/Deno, where it sets `location` to a watch page. In a
         * browser that assignment navigates the page away and wipes everything, so drop it: the
         * page's own location is already https://www.youtube.com/.
         */
        fun browserSafe(core: String): String {
            if (!core.contains(LOCATION_ASSIGNMENT)) Log.w(TAG, "Solver changed: location assignment not found")
            return core.replace(LOCATION_ASSIGNMENT, "void 0;")
        }

        /** Quotes [s] as a JavaScript string literal. */
        fun js(s: String): String = JSONObject.quote(s)
    }
}
