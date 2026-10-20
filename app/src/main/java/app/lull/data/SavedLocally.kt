package app.lull.data

import android.content.Context
import app.lull.core.CollectionKind
import app.lull.core.InnerTube
import app.lull.core.MusicCollection
import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlists, albums and podcasts you saved from Lull. YouTube Music's library lists can
 * leave some out (YouTube playlists in particular, or for a while after saving), so Lull
 * remembers what you saved and adds any missing ones to the top of the matching list.
 */
class SavedLocally(context: Context) {
    private val prefs = context.getSharedPreferences("saved_locally", Context.MODE_PRIVATE)

    fun all(): List<MusicCollection> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MusicCollection(
                o.getString("id"), o.getString("title"), o.optString("subtitle"),
                runCatching { CollectionKind.valueOf(o.getString("kind")) }.getOrDefault(CollectionKind.PLAYLIST),
            )
        }
    }.getOrDefault(emptyList())

    fun set(collection: MusicCollection, saved: Boolean) {
        val list = all().filter { it.browseId != collection.browseId }.let { if (saved) listOf(collection) + it else it }
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().put("id", c.browseId).put("title", c.title).put("subtitle", c.subtitle).put("kind", c.kind.name))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** [serverItems] from one library shelf, with your Lull-saved ones of that kind added on top. */
    fun merge(shelfBrowseId: String, serverItems: List<MusicCollection>): List<MusicCollection> {
        val kind = when (shelfBrowseId) {
            InnerTube.LIBRARY_PLAYLISTS -> CollectionKind.PLAYLIST
            InnerTube.LIBRARY_ALBUMS -> CollectionKind.ALBUM
            InnerTube.LIBRARY_PODCASTS -> CollectionKind.PODCAST
            else -> return serverItems
        }
        val have = serverItems.map { it.browseId.removePrefix("VL") }.toSet()
        val missing = all().filter { it.kind == kind && it.browseId.removePrefix("VL") !in have }
        return missing + serverItems
    }

    private companion object { const val KEY = "items" }
}
