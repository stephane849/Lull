package app.lull.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
        /** Saved podcast shows, plus "New episodes" and "Episodes for later". */
        const val LIBRARY_PODCASTS = "FEmusic_library_non_music_audio_list"

        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
        private val JSON_TYPE = "application/json".toMediaType()
    }

    val isSignedIn: Boolean get() = credentials()?.sapisid != null

    /** Songs, or podcast episodes with [SearchFilter.EPISODES]. */
    fun searchTracks(query: String, continuation: String? = null, filter: SearchFilter = SearchFilter.SONGS): Page<Track> {
        val root = post("search", search(query, filter), continuation)
        return Page(Parser.tracks(root), Parser.continuation(root))
    }

    fun searchCollections(query: String, filter: SearchFilter, continuation: String? = null): Page<MusicCollection> {
        val root = post("search", search(query, filter), continuation)
        return Page(Parser.collections(root), Parser.continuation(root))
    }

    /** Library shelves: [LIBRARY_PLAYLISTS], [LIBRARY_ALBUMS], [LIBRARY_ARTISTS] or [LIBRARY_PODCASTS]. */
    fun libraryCollections(browseId: String, continuation: String? = null): Page<MusicCollection> {
        val root = post("browse", browse(browseId), continuation)
        return Page(Parser.collections(root), Parser.continuation(root))
    }

    /** Songs or episodes inside a playlist, album, artist or podcast page, or [LIKED_SONGS]. */
    fun collectionTracks(browseId: String, continuation: String? = null): Page<Track> =
        collectionPage(browseId, continuation).tracks

    /** Like [collectionTracks], plus the page's library state on the first page. */
    fun collectionPage(browseId: String, continuation: String? = null): CollectionPage {
        val root = post("browse", browse(browseId), continuation)
        return CollectionPage(
            Page(Parser.tracks(root), Parser.continuation(root)),
            if (continuation == null) Parser.libraryState(root) else null,
            continuation == null && browseId != LIKED_SONGS && Parser.isEditable(root),
        )
    }

    /** Save an album, podcast or playlist to your library, or remove it. */
    fun setSaved(playlistId: String, saved: Boolean) {
        val target = buildJsonObject { put("playlistId", playlistId) }
        post(if (saved) "like/like" else "like/removelike", mapOf("target" to target), null)
    }

    /** Your playlists that this song can be added to. */
    fun playlistTargets(videoId: String): List<PlaylistTarget> {
        val root = post("playlist/get_add_to_playlist", mapOf("videoIds" to buildJsonArray { add(videoId) }), null)
        return Parser.playlistTargets(root)
    }

    fun addToPlaylist(playlistId: String, videoId: String) {
        val action = buildJsonObject {
            put("action", "ACTION_ADD_VIDEO")
            put("addedVideoId", videoId)
            put("dedupeOption", "DEDUPE_OPTION_SKIP")
        }
        post(
            "browse/edit_playlist",
            mapOf("playlistId" to JsonPrimitive(playlistId.removePrefix("VL")), "actions" to buildJsonArray { add(action) }),
            null,
        )
    }

    /**
     * Moves an entry of your playlist so it sits just before [beforeSetVideoId], or last when
     * that is null (done as two moves, since YouTube only moves "before" something).
     */
    fun movePlaylistItem(playlistId: String, setVideoId: String, beforeSetVideoId: String?, lastSetVideoId: String?) {
        if (beforeSetVideoId != null) {
            editPlaylist(playlistId, moveAction(setVideoId, beforeSetVideoId))
        } else if (lastSetVideoId != null && lastSetVideoId != setVideoId) {
            editPlaylist(playlistId, moveAction(setVideoId, lastSetVideoId))
            editPlaylist(playlistId, moveAction(lastSetVideoId, setVideoId))
        }
    }

    fun removePlaylistItem(playlistId: String, setVideoId: String, videoId: String) =
        editPlaylist(playlistId, buildJsonObject {
            put("action", "ACTION_REMOVE_VIDEO")
            put("setVideoId", setVideoId)
            put("removedVideoId", videoId)
        })

    fun renamePlaylist(playlistId: String, name: String) =
        editPlaylist(playlistId, buildJsonObject {
            put("action", "ACTION_SET_PLAYLIST_NAME")
            put("playlistName", name)
        })

    fun deletePlaylist(playlistId: String) {
        post("playlist/delete", mapOf("playlistId" to JsonPrimitive(playlistId.removePrefix("VL"))), null)
    }

    private fun moveAction(setVideoId: String, beforeSetVideoId: String) = buildJsonObject {
        put("action", "ACTION_MOVE_VIDEO_BEFORE")
        put("setVideoId", setVideoId)
        put("movedSetVideoIdSuccessor", beforeSetVideoId)
    }

    private fun editPlaylist(playlistId: String, action: JsonObject) {
        post(
            "browse/edit_playlist",
            mapOf("playlistId" to JsonPrimitive(playlistId.removePrefix("VL")), "actions" to buildJsonArray { add(action) }),
            null,
        )
    }

    /** A new private playlist holding [videoIds]. Returns its id. */
    fun createPlaylist(title: String, videoIds: List<String>): String? {
        val root = post(
            "playlist/create",
            mapOf(
                "title" to JsonPrimitive(title),
                "description" to JsonPrimitive(""),
                "privacyStatus" to JsonPrimitive("PRIVATE"),
                "videoIds" to buildJsonArray { videoIds.forEach { add(it) } },
            ),
            null,
        )
        return Parser.createdPlaylistId(root)
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
