package app.lull.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class InnerTubeException(val code: Int, message: String) : IOException(message) {
    val isAuthError: Boolean get() = code == 401 || code == 403
}

/**
 * A small client for YouTube Music's internal web API ("InnerTube"), signed in with the
 * user's own session. This API is unofficial and undocumented; it can change without notice.
 *
 * All calls are blocking: call them from a background thread.
 */
class InnerTube(
    private val http: OkHttpClient,
    private val credentials: () -> Credentials?,
) {
    companion object {
        const val ORIGIN = "https://music.youtube.com"

        /** Web client version. Bump if Google starts rejecting requests (HTTP 400). */
        const val CLIENT_VERSION = "1.20251006.03.00"

        const val LIKED_SONGS = "VLLM"
        const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
        const val LIBRARY_ALBUMS = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS = "FEmusic_library_corpus_track_artists"

        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    val isSignedIn: Boolean get() = credentials()?.sapisid != null

    fun searchTracks(query: String, continuation: String? = null): Page<Track> {
        val root = post("search", search(query, SearchFilter.SONGS), continuation)
        return Page(Parser.tracks(root), Parser.continuation(root))
    }

    fun searchCollections(query: String, filter: SearchFilter, continuation: String? = null): Page<MusicCollection> {
        val root = post("search", search(query, filter), continuation)
        return Page(Parser.collections(root), Parser.continuation(root))
    }

    /** Library shelves: [LIBRARY_PLAYLISTS], [LIBRARY_ALBUMS] or [LIBRARY_ARTISTS]. */
    fun libraryCollections(browseId: String, continuation: String? = null): Page<MusicCollection> {
        val root = post("browse", browse(browseId), continuation)
        return Page(Parser.collections(root), Parser.continuation(root))
    }

    /** Songs inside a playlist, album or artist page, or [LIKED_SONGS]. */
    fun collectionTracks(browseId: String, continuation: String? = null): Page<Track> {
        val root = post("browse", browse(browseId), continuation)
        return Page(Parser.tracks(root), Parser.continuation(root))
    }

    /** A radio queue seeded from one song. Only used when the user asks for it. */
    fun radio(videoId: String): List<Track> {
        val root = post(
            "next",
            mapOf(
                "videoId" to JsonPrimitive(videoId),
                "playlistId" to JsonPrimitive("RDAMVM$videoId"),
                "isAudioOnly" to JsonPrimitive(true),
            ),
            null,
        )
        return Parser.radioTracks(root)
    }

    fun setLiked(videoId: String, liked: Boolean) {
        val target = buildJsonObject { put("videoId", videoId) }
        post(if (liked) "like/like" else "like/removelike", mapOf("target" to target), null)
    }

    private fun search(query: String, filter: SearchFilter) = mapOf(
        "query" to JsonPrimitive(query),
        "params" to JsonPrimitive(filter.params),
    )

    private fun browse(browseId: String) = mapOf("browseId" to JsonPrimitive(browseId))

    private fun post(endpoint: String, fields: Map<String, JsonElement>, continuation: String?): JsonElement {
        val body: JsonObject = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                }
                putJsonObject("user") {}
            }
            if (continuation == null) fields.forEach { (k, v) -> put(k, v) }
            else put("continuation", continuation)
        }

        val url = "$ORIGIN/youtubei/v1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false")
            .apply {
                if (continuation != null) {
                    addQueryParameter("ctoken", continuation)
                    addQueryParameter("continuation", continuation)
                    addQueryParameter("type", "next")
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", CLIENT_VERSION)
            .apply {
                credentials()?.let { creds ->
                    header("Cookie", creds.cookie)
                    creds.authorization(ORIGIN)?.let { header("Authorization", it) }
                }
            }
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw InnerTubeException(response.code, "YouTube Music returned ${response.code}")
            }
            val text = response.body?.string() ?: throw InnerTubeException(response.code, "Empty response")
            return Json.parseToJsonElement(text)
        }
    }
}
