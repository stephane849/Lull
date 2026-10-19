package app.lull.data

import android.content.Context

/**
 * Where you stopped in long listening (classes, podcast episodes, anything over 10 minutes),
 * so it picks up there next time. Cleared once the item plays to the end.
 */
class ResumePositions(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    fun get(videoId: String): Long = prefs.getLong(videoId, 0L)
    fun set(videoId: String, positionMs: Long) = prefs.edit().putLong(videoId, positionMs).apply()
    fun clear(videoId: String) = prefs.edit().remove(videoId).apply()
}
