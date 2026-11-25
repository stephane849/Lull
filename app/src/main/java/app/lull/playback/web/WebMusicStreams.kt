package app.lull.playback.web

import android.net.Uri
import android.util.Base64
import app.lull.core.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.net.URLDecoder

/**
 * Finds audio the way YouTube Music's own web player does, for videos where YouTube refuses the
 * links NewPipe Extractor gets (HTTP 403 without a proof-of-origin token):
 *
 * 1. a visitor session and BotGuard tokens for it ([WebEngine], once per ~12 hours);
 * 2. a WEB_REMIX player request carrying the video's token, signed in with the user's own
 *    session when there is one (album tracks are often unavailable to anonymous requests),
 *    otherwise, or if that fails, anonymous;
 * 3. the stream URL's `sig` / `n` challenges solved against YouTube's current player script;
 * 4. a streaming token appended as `pot`: bound to the account's data-sync id when signed in,
 *    to the visitor id otherwise.
 */
class WebMusicStreams(
    private val http: OkHttpClient,
    private val engine: WebEngine,
    private val credentials: () -> Credentials?,
) {

    /** YouTube Music answered but won't play this song; a fresh session wouldn't change that. */
    class Unplayable(message: String) : IOException(message)

    /** An audio URL plus the headers it must be fetched with. */
    class Stream(val url: String, val headers: Map<String, String>)

    private class Session(val visitorData: String, val streamingPot: String, val expiresAt: Long)
    private class Player(val id: String, val signatureTimestamp: Int)

    private var session: Session? = null
    /** Streaming tokens by what they're bound to (visitor id or data-sync id), for this session. */
    private val streamingPots = HashMap<String, String>()
    private var player: Player? = null
    private var playerCheckedAt = 0L

    /** Forgets the session and player state, e.g. after the WebView was lost. */
    @Synchronized
    fun invalidate() {
        session = null
        streamingPots.clear()
        player = null
        engine.reset()
    }

    @Synchronized
    fun audio(videoId: String, high: Boolean): Stream {
        if (engine.stateLost) {
            // A new page has none of the old tokens or the prepared player.
            engine.stateLost = false
            session = null
            streamingPots.clear()
            player = null
        }
        val s = session()
        val p = player()
        val playerPot = mint(videoId)

        // Signed in first: that's how YouTube Music plays it for this user. Then anonymous.
        val creds = credentials()?.takeIf { it.sapisid != null }
        val problems = mutableListOf<String>()
        var response: JSONObject? = null
        var binding = s.visitorData
        if (creds != null) {
            val signedIn = runCatching { playerResponse(videoId, s.visitorData, playerPot, p.signatureTimestamp, creds) }
            val r = signedIn.getOrNull()
            if (r != null && playable(r)) {
                response = r
                // Signed-in streams are bound to the account session, as on music.youtube.com.
                binding = r.optJSONObject("responseContext")?.optJSONObject("mainAppWebResponseContext")
                    ?.optString("datasyncId")?.takeIf { it.isNotEmpty() } ?: s.visitorData
            } else {
                problems += "signed in: " + (r?.let(::unplayable) ?: signedIn.exceptionOrNull()?.message.orEmpty())
            }
        }
        if (response == null) {
            val r = playerResponse(videoId, s.visitorData, playerPot, p.signatureTimestamp, null)
            if (playable(r)) response = r else problems += (if (creds != null) "anonymous: " else "") + unplayable(r)
        }
        if (response == null) throw Unplayable("YouTube Music: " + problems.joinToString("; "))
        val formats = response.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: JSONArray()
        val audio = (0 until formats.length()).map { formats.getJSONObject(it) }
            .filter { it.optString("mimeType").startsWith("audio/") }
            .filter { it.has("url") || it.has("signatureCipher") }
        if (audio.isEmpty()) throw IOException("YouTube Music returned no audio links")

        // Original language (not AI dubs or audio description), and no loudness-compressed copies.
        val original = audio.filter { f -> f.optJSONObject("audioTrack")?.optBoolean("audioIsDefault", true) ?: true }
            .ifEmpty { audio }
        val plain = original.filterNot { it.optBoolean("isDrc") }.ifEmpty { original }
        fun kbps(f: JSONObject) = (f.optInt("averageBitrate").takeIf { it > 0 } ?: f.optInt("bitrate")) / 1000
        val chosen = if (high) {
            plain.maxBy(::kbps)
        } else {
            plain.filter { kbps(it) in 1..NORMAL_MAX_KBPS }.maxByOrNull(::kbps) ?: plain.minBy(::kbps)
        }

        val base = solve(chosen)
        val headers = mapOf("User-Agent" to WebEngine.USER_AGENT, "Origin" to MUSIC, "Referer" to "$MUSIC/")

        // Check the link before the player gets it. Signed in, the streaming token should be bound
        // to the account session (with or without its "||" suffix); the visitor id is the last try.
        val bindings = listOf(binding, binding.substringBefore("||"), s.visitorData).filter { it.isNotEmpty() }.distinct()
        val refused = mutableListOf<String>()
        for (b in bindings) {
            val pot = if (b == s.visitorData) s.streamingPot else streamingPots.getOrPut(b) { mint(b) }
            val url = "$base&pot=${Uri.encode(pot)}"
            val code = probe(url, headers)
            if (code in 200..299) return Stream(url, headers)
            refused += code.toString()
        }
        throw IOException("YouTube refused the audio (HTTP ${refused.joinToString("/")})")
    }

    /** The HTTP status for the first two bytes of [url]. */
    private fun probe(url: String, headers: Map<String, String>): Int {
        val request = Request.Builder().url(url).header("Range", "bytes=0-1")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(request).execute().use { return it.code }
    }

    private fun playable(r: JSONObject) = r.optJSONObject("playabilityStatus")?.optString("status") == "OK"

    private fun unplayable(r: JSONObject): String {
        val status = r.optJSONObject("playabilityStatus")
        return "${status?.optString("status")} ${status?.optString("reason").orEmpty()}".trim()
    }

    // region Tokens

    private fun session(): Session {
        session?.takeIf { System.currentTimeMillis() < it.expiresAt }?.let { return it }
        val visitorData = visitorData()

        val challenge = parseChallenge(jnn("Create", "[\"$REQUEST_KEY\"]"))
        val botguardResponse = engine.eval(
            "runBotGuard($challenge).then(function (r) { window.webPoSignalOutput = r.webPoSignalOutput; return r.botguardResponse; })",
        )
        val generated = JSONArray(jnn("GenerateIT", "[\"$REQUEST_KEY\", ${WebEngine.js(botguardResponse)}]"))
        val integrityToken = decodeBase64(generated.getString(0)).joinToString(",") { b -> (b.toInt() and 0xFF).toString() }
        engine.eval("(window.integrityToken = new Uint8Array([$integrityToken]), 'ok')")
        val ttlSeconds = generated.optLong(1, 6 * 3600L)

        // The streaming token is minted from the visitor id, before any per-video token.
        val streamingPot = mint(visitorData)
        return Session(visitorData, streamingPot, System.currentTimeMillis() + (ttlSeconds - 600) * 1000)
            .also { session = it }
    }

    private fun mint(identifier: String): String {
        val bytes = engine.eval(
            "Array.from(obtainPoToken(window.webPoSignalOutput, window.integrityToken, new TextEncoder().encode(${WebEngine.js(identifier)}))).join(',')",
        )
        val raw = bytes.split(',').map { it.trim().toInt().toByte() }.toByteArray()
        return Base64.encodeToString(raw, Base64.NO_WRAP).replace('+', '-').replace('/', '_')
    }

    private fun visitorData(): String {
        val body = JSONObject().put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB").put("clientVersion", WEB_VERSION).put("hl", "en").put("gl", "US")))
        val json = postJson("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false", body, emptyMap())
        val raw = json.optJSONObject("responseContext")?.optString("visitorData").orEmpty()
        if (raw.isEmpty()) throw IOException("No visitor session from YouTube")
        return URLDecoder.decode(raw, "UTF-8")
    }

    private fun jnn(method: String, body: String): String {
        val request = Request.Builder()
            .url("https://www.youtube.com/api/jnn/v1/$method")
            .post(body.toRequestBody("application/json+protobuf".toMediaType()))
            .header("User-Agent", WebEngine.USER_AGENT)
            .header("Accept", "application/json")
            .header("x-goog-api-key", BOTGUARD_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .build()
        http.newCall(request).execute().use { r ->
            if (r.code != 200) throw IOException("BotGuard $method: HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    /** The challenge from jnn/Create, as a JavaScript object literal for runBotGuard(). */
    private fun parseChallenge(raw: String): String {
        val scrambled = JSONArray(raw)
        val data = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
            val bytes = decodeBase64(scrambled.getString(1)).map { (it + 97).toByte() }.toByteArray()
            JSONArray(String(bytes, Charsets.UTF_8))
        } else {
            scrambled.getJSONArray(0)
        }
        fun firstString(index: Int): String? = data.optJSONArray(index)?.let { a ->
            (0 until a.length()).map { a.opt(it) }.firstOrNull { it is String } as String?
        }
        return JSONObject()
            .put("messageId", data.optString(0))
            .put("interpreterJavascript", JSONObject()
                .put("privateDoNotAccessOrElseSafeScriptWrappedValue", firstString(1))
                .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", firstString(2)))
            .put("interpreterHash", data.optString(3))
            .put("program", data.optString(4))
            .put("globalName", data.optString(5))
            .put("clientExperimentsStateBlob", data.optString(7))
            .toString()
    }

    // endregion

    // region Player script and challenges

    /** YouTube's current player id and its signature timestamp; rechecked every few hours. */
    private fun player(): Player {
        val known = player
        if (known != null && System.currentTimeMillis() - playerCheckedAt < PLAYER_RECHECK_MS) return known
        val iframe = get("https://www.youtube.com/iframe_api")
        val id = Regex("""player\\?/([0-9a-fA-F]{8})\\?/""").find(iframe)?.groupValues?.get(1)
            ?: throw IOException("Couldn't find YouTube's player version")
        playerCheckedAt = System.currentTimeMillis()
        if (known?.id == id) return known

        val source = get("https://www.youtube.com/s/player/$id/tv-player-ias.vflset/tv-player-ias.js")
        val sts = Regex("""(?:signatureTimestamp|sts)\s*:\s*(\d{5})""").find(source)?.groupValues?.get(1)?.toInt()
            ?: throw IOException("Couldn't read the player's signature timestamp")
        // Parse the player once in the WebView and keep the preprocessed form there.
        engine.providePlayer(source)
        try {
            engine.eval(
                "(function () { var r = jsc({type: 'player', player: Lull.player(), output_preprocessed: true, requests: []});" +
                    " window.lullPlayer = r.preprocessed_player; return 'ok'; })()",
                timeoutSeconds = 30,
            )
        } finally {
            engine.releasePlayer()
        }
        return Player(id, sts).also { player = it }
    }

    /** The format's URL with its `sig` and `n` challenges solved. */
    private fun solve(format: JSONObject): String {
        // Parsed by hand: android.net.Uri treats a stray '#' or ':' as URL structure and drops the rest.
        val cipher = format.optString("signatureCipher").takeIf { it.isNotEmpty() }?.let(::queryParams)
        val base = format.optString("url").ifEmpty { cipher?.get("url").orEmpty() }
        if (base.isEmpty()) {
            throw IOException("No stream URL (format has: ${format.keys().asSequence().joinToString(",")})")
        }
        val sig = cipher?.get("s")
        val sigParam = cipher?.get("sp") ?: "signature"
        val n = Regex("""[?&]n=([^&]*)""").find(base)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }

        val requests = JSONArray()
        if (sig != null) requests.put(JSONObject().put("type", "sig").put("challenges", JSONArray().put(sig)))
        if (n != null) requests.put(JSONObject().put("type", "n").put("challenges", JSONArray().put(n)))
        if (requests.length() == 0) return base

        val result = JSONObject(engine.eval(
            "jsc({type: 'preprocessed', preprocessed_player: window.lullPlayer, requests: $requests})",
        ))
        val responses = result.getJSONArray("responses")
        fun answer(index: Int, challenge: String): String {
            val r = responses.getJSONObject(index)
            if (r.optString("type") != "result") throw IOException("Challenge not solved: ${r.optString("error").take(200)}")
            return r.getJSONObject("data").getString(challenge)
        }

        var i = 0
        var url = base
        // Edit the URL as text so every signed parameter keeps its exact encoding.
        if (sig != null) url += "&$sigParam=" + Uri.encode(answer(i++, sig))
        if (n != null) {
            val solved = Uri.encode(answer(i, n))
            url = url.replace(Regex("""([?&])n=[^&]*""")) { m -> "${m.groupValues[1]}n=$solved" }
        }
        return url
    }

    // endregion

    private fun playerResponse(videoId: String, visitorData: String, poToken: String, sts: Int, creds: Credentials?): JSONObject {
        val version = runCatching { YoutubeParsingHelper.getYoutubeMusicClientVersion() }.getOrDefault(MUSIC_VERSION)
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX").put("clientVersion", version)
                .put("hl", "en").put("gl", "US")
                // Signed in, the account's own session identifies the client; don't mix in the anonymous visitor.
                .apply { if (creds == null) put("visitorData", visitorData) }))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("signatureTimestamp", sts)))
            .put("serviceIntegrityDimensions", JSONObject().put("poToken", poToken))
        val headers = mutableMapOf(
            "Origin" to MUSIC,
            "X-Youtube-Client-Name" to "67",
            "X-Youtube-Client-Version" to version,
        )
        if (creds == null) headers["X-Goog-Visitor-Id"] = visitorData
        if (creds != null) {
            // Signed the way Lull's InnerTube client signs library requests.
            headers["Cookie"] = creds.cookie
            creds.authorization(MUSIC)?.let { headers["Authorization"] = it }
            headers["X-Goog-AuthUser"] = "0"
            headers["X-Origin"] = MUSIC
            headers["Referer"] = "$MUSIC/"
        }
        return postJson("$MUSIC/youtubei/v1/player?prettyPrint=false", body, headers)
    }

    private fun postJson(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val request = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", WebEngine.USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${Uri.parse(url).path}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", WebEngine.USER_AGENT).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${Uri.parse(url).path}")
            return r.body?.string().orEmpty()
        }
    }

    /** `a=1&b=x%3Dy` as a map, values decoded. */
    private fun queryParams(query: String): Map<String, String> = query.split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq) to URLDecoder.decode(part.substring(eq + 1), "UTF-8")
    }.toMap()

    private fun decodeBase64(s: String): ByteArray =
        Base64.decode(s.replace('-', '+').replace('_', '/').replace('.', '='), Base64.DEFAULT)

    private companion object {
        const val MUSIC = "https://music.youtube.com"
        const val NORMAL_MAX_KBPS = 140
        const val PLAYER_RECHECK_MS = 6 * 60 * 60 * 1000L
        const val WEB_VERSION = "2.20251120.01.00"
        const val MUSIC_VERSION = "1.20251119.03.00"

        // Public BotGuard key and request key used by YouTube's web pages (as in NewPipe).
        const val BOTGUARD_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    }
}
