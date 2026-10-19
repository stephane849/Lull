package app.lull.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pulls tracks and collections out of InnerTube responses.
 *
 * Rather than following exact paths (which Google reshuffles often), it walks the whole tree
 * and reads the renderer objects wherever they appear. That keeps it working across most
 * layout changes.
 */
object Parser {

    private val DURATION = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
    private val TYPE_WORDS = setOf("Song", "Video", "Episode", "Single", "EP", "Album", "Playlist", "Artist")

    fun tracks(root: JsonElement): List<Track> =
        findAll(root, "musicResponsiveListItemRenderer")
            .mapNotNull(::trackFrom)
            .distinctBy { it.videoId }

    fun collections(root: JsonElement): List<MusicCollection> =
        (findAll(root, "musicResponsiveListItemRenderer").mapNotNull(::collectionFromList) +
            findAll(root, "musicTwoRowItemRenderer").mapNotNull(::collectionFromTwoRow))
            .distinctBy { it.browseId }

    fun radioTracks(root: JsonElement): List<Track> =
        findAll(root, "playlistPanelVideoRenderer").mapNotNull { r ->
            val id = r["videoId"].str() ?: return@mapNotNull null
            val title = runsText(r["title"]).ifBlank { return@mapNotNull null }
            val artist = r["longBylineText"].field("runs").arr()?.firstOrNull().field("text").str()
                ?: runsText(r["shortBylineText"])
            Track(id, title, artist, parseDuration(runsText(r["lengthText"])))
        }.distinctBy { it.videoId }

    fun continuation(root: JsonElement): String? =
        findAll(root, "nextContinuationData").firstNotNullOfOrNull { it["continuation"].str() }
            ?: findAll(root, "continuationCommand").firstNotNullOfOrNull { it["token"].str() }

    fun parseDuration(text: String?): Int? {
        if (text == null || !DURATION.matches(text.trim())) return null
        return text.trim().split(':').fold(0) { acc, part -> acc * 60 + part.toInt() }
    }

    // --- renderers ---------------------------------------------------------------------

    private fun trackFrom(r: JsonObject): Track? {
        val videoId = path(r, "playlistItemData", "videoId").str()
            ?: findAll(r, "watchEndpoint").firstNotNullOfOrNull { it["videoId"].str() }
            ?: return null
        val columns = columnRuns(r)
        val title = columns.firstOrNull()?.joinToString("") { it.field("text").str().orEmpty() }?.trim()
        if (title.isNullOrEmpty()) return null

        val rest = columns.drop(1).flatten()
        val artistRuns = rest.filter { pageType(it.field("navigationEndpoint").field("browseEndpoint")) == "MUSIC_PAGE_TYPE_ARTIST" }
            .mapNotNull { it.field("text").str() }
        val texts = rest.mapNotNull { it.field("text").str()?.trim() }
        val artist = if (artistRuns.isNotEmpty()) artistRuns.joinToString(", ")
        else texts.firstOrNull { t ->
            t.isNotEmpty() && t != "•" && t !in TYPE_WORDS && !DURATION.matches(t) &&
                !t.contains("plays") && !t.contains("views")
        }.orEmpty()

        val fixed = runsText(path(r, "fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text"))
        val duration = parseDuration(fixed) ?: texts.firstNotNullOfOrNull { parseDuration(it) }
        return Track(videoId, title, artist, duration)
    }

    private fun collectionFromList(r: JsonObject): MusicCollection? {
        if (path(r, "playlistItemData", "videoId").str() != null) return null
        val browse = path(r, "navigationEndpoint", "browseEndpoint") ?: return null
        val id = browse.field("browseId").str() ?: return null
        val columns = columnRuns(r)
        val title = columns.firstOrNull()?.joinToString("") { it.field("text").str().orEmpty() }?.trim()
        if (title.isNullOrEmpty()) return null
        val subtitle = columns.drop(1).flatten().joinToString("") { it.field("text").str().orEmpty() }.trim()
        return MusicCollection(id, title, subtitle, kindOf(browse))
    }

    private fun collectionFromTwoRow(r: JsonObject): MusicCollection? {
        val browse = path(r, "navigationEndpoint", "browseEndpoint") ?: return null
        val id = browse.field("browseId").str() ?: return null
        val title = runsText(r["title"]).trim().ifEmpty { return null }
        return MusicCollection(id, title, runsText(r["subtitle"]).trim(), kindOf(browse))
    }

    private fun kindOf(browse: JsonElement): CollectionKind = when (pageType(browse)) {
        "MUSIC_PAGE_TYPE_PLAYLIST" -> CollectionKind.PLAYLIST
        "MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK" -> CollectionKind.ALBUM
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" -> CollectionKind.ARTIST
        else -> {
            val id = browse.field("browseId").str().orEmpty()
            when {
                id.startsWith("VL") -> CollectionKind.PLAYLIST
                id.startsWith("MPRE") -> CollectionKind.ALBUM
                id.startsWith("UC") || id.startsWith("MPLA") -> CollectionKind.ARTIST
                else -> CollectionKind.OTHER
            }
        }
    }

    private fun pageType(browse: JsonElement?): String? =
        path(browse, "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType").str()

    private fun columnRuns(r: JsonObject): List<List<JsonElement>> =
        r["flexColumns"].arr().orEmpty().map { col ->
            path(col, "musicResponsiveListItemFlexColumnRenderer", "text", "runs").arr().orEmpty()
        }

    // --- tiny JSON helpers ---------------------------------------------------------------

    private fun findAll(root: JsonElement?, key: String): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        fun walk(e: JsonElement?) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    if (k == key && v is JsonObject) out.add(v)
                    walk(v)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private fun path(e: JsonElement?, vararg keys: Any): JsonElement? =
        keys.fold(e) { cur, k ->
            when (k) {
                is String -> (cur as? JsonObject)?.get(k)
                is Int -> (cur as? JsonArray)?.getOrNull(k)
                else -> null
            }
        }

    private fun runsText(e: JsonElement?): String =
        e.field("runs").arr()?.joinToString("") { it.field("text").str().orEmpty() }
            ?: e.field("simpleText").str().orEmpty()

    private fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
