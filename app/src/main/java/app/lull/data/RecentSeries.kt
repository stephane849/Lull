package app.lull.data

import android.content.Context
import app.lull.core.CollectionKind
import app.lull.core.MusicCollection
import app.lull.core.Track
import org.json.JSONArray
import org.json.JSONObject

/** The series (class, podcast, lecture playlist) you last played from, for the home screen. */
data class Series(val collection: MusicCollection, val tracks: List<Track>)

/**
 * Remembers the last series played, with its track list, so the home screen can offer
 * "Pick up" without going to the network. Only lists with long items are kept.
 */
class RecentSeries(context: Context) {
    private val prefs = context.getSharedPreferences("recent_series", Context.MODE_PRIVATE)

    fun last(): Series? = runCatching {
        val o = JSONObject(prefs.getString(KEY, null) ?: return null)
        val c = MusicCollection(
            o.getString("id"), o.getString("title"), o.optString("subtitle"),
            runCatching { CollectionKind.valueOf(o.getString("kind")) }.getOrDefault(CollectionKind.OTHER),
        )
        val arr = o.getJSONArray("tracks")
        val tracks = (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            Track(t.getString("v"), t.getString("t"), t.optString("a"), t.optInt("d", -1).takeIf { it >= 0 })
        }
        Series(c, tracks)
    }.getOrNull()

    fun save(collection: MusicCollection, tracks: List<Track>) {
        val arr = JSONArray()
        tracks.take(MAX_TRACKS).forEach { t ->
            arr.put(JSONObject().put("v", t.videoId).put("t", t.title).put("a", t.artist).put("d", t.durationSec ?: -1))
        }
        val o = JSONObject()
            .put("id", collection.browseId).put("title", collection.title)
            .put("subtitle", collection.subtitle).put("kind", collection.kind.name)
            .put("tracks", arr)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "last"
        const val MAX_TRACKS = 300
    }
}
