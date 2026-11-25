package app.lull.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import android.util.Log
import app.lull.data.Settings
import app.lull.playback.web.WebMusicStreams
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns `lull://video/<id>[?q=high|normal]` into a playable audio URL at the moment the
 * player or the downloader needs it. Stream URLs expire after a few hours, so they are
 * never stored in the queue, only cached here briefly.
 *
 * YouTube now refuses (HTTP 403) the links NewPipe Extractor finds for most videos, so the
 * first choice is [WebMusicStreams] (YouTube Music's web player, with BotGuard tokens). NewPipe
 * stays as the fallback.
 */
class StreamResolver(
    context: Context,
    private val settings: Settings,
    private val web: WebMusicStreams,
) : ResolvingDataSource.Resolver {

    private class Resolved(val url: String, val headers: Map<String, String>, val expires: Long)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var webFailedAt = 0L
    private val problems = ConcurrentHashMap<String, String>()

    /** Why the last lookup for [videoId] failed, in a few words per method; null if it didn't. */
    fun problemFor(videoId: String): String? = problems[videoId]
    private val cache = ConcurrentHashMap<String, Resolved>() // key includes "web" so older picks aren't reused

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec
        val videoId = uri.lastPathSegment ?: throw IOException("Missing video id")
        val high = when (uri.getQueryParameter("q")) {
            "high" -> true
            "normal" -> false
            else -> if (connectivity?.isActiveNetworkMetered == true) settings.mobileHighQuality else settings.wifiHighQuality
        }
        val resolved = resolve(videoId, high)
        return dataSpec.buildUpon()
            .setUri(resolved.url)
            .setKey(dataSpec.key ?: videoId)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
            .build()
    }

    private fun resolve(videoId: String, high: Boolean): Resolved {
        val key = "$videoId|$high|web"
        cache[key]?.let { if (System.currentTimeMillis() < it.expires) return it }

        val resolved = try {
            // ExoPlayer retries failed loads; don't redo a slow failure on every retry.
            if (System.currentTimeMillis() - webFailedAt < WEB_BACKOFF_MS) throw IOException("Web path failed moments ago")
            webStream(videoId, high)
        } catch (webError: Exception) {
            if (webError.message != "Web path failed moments ago") webFailedAt = System.currentTimeMillis()
            Log.w(TAG, "YouTube Music web path failed for $videoId, trying NewPipe", webError)
            try {
                // Cached only briefly, so the web path gets another go soon.
                Resolved(newPipeUrl(videoId, high), emptyMap(), System.currentTimeMillis() + WEB_BACKOFF_MS)
            } catch (e: IOException) {
                problems[videoId] = "web: ${brief(webError)} / old: ${brief(e)}"
                e.addSuppressed(webError)
                throw e
            }
        }
        problems.remove(videoId)
        val expires = if (resolved.expires > 0) resolved.expires else System.currentTimeMillis() + URL_TTL_MS
        return Resolved(resolved.url, resolved.headers, expires).also { cache[key] = it }
    }

    /** One retry with a fresh WebView and session: tokens or the page state may have gone stale. */
    private fun webStream(videoId: String, high: Boolean): Resolved {
        val stream = try {
            web.audio(videoId, high)
        } catch (first: Exception) {
            Log.w(TAG, "Web path failed for $videoId, retrying with a fresh session", first)
            web.invalidate()
            web.audio(videoId, high)
        }
        return Resolved(stream.url, stream.headers, 0)
    }

    private fun newPipeUrl(videoId: String, high: Boolean): String {
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: Exception) {
            throw IOException("Couldn't load song $videoId", e)
        }
        val all = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        if (all.isEmpty()) throw IOException("No audio stream for $videoId")
        // Many videos now carry AI-dubbed tracks in other languages (and audio description).
        // Always play the original; fall back to any non-descriptive track, then anything.
        val streams = all.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { all.filter { it.audioTrackType != AudioTrackType.DESCRIPTIVE } }
            .ifEmpty { all }

        val chosen = if (high) {
            streams.maxBy { it.averageBitrate }
        } else {
            streams.filter { it.averageBitrate in 1..NORMAL_MAX_KBPS }.maxByOrNull { it.averageBitrate }
                ?: streams.minBy { it.averageBitrate }
        }
        return chosen.content
    }

    /** The most specific message in [e]'s cause chain, shortened for the Now playing screen. */
    private fun brief(e: Throwable): String {
        var deepest: Throwable = e
        var t: Throwable? = e
        while (t != null) {
            if (!t.message.isNullOrBlank() && !t.message!!.startsWith("Couldn't load song")) deepest = t
            t = t.cause
        }
        val text = deepest.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            .ifEmpty { deepest.javaClass.simpleName }
        return if (text.length > 90) text.take(90) + "…" else text
    }

    companion object {
        const val SCHEME = "lull"
        private const val TAG = "LullStreams"
        private const val WEB_BACKOFF_MS = 60_000L
        private const val NORMAL_MAX_KBPS = 140
        private const val URL_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
