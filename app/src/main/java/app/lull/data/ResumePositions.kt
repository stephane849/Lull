package app.lull.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listening progress for long items (classes, podcast episodes, anything over 10 minutes):
 * where you stopped, whether you finished, and when you last played it. Shared by the
 * playback service and the screens; [version] bumps on every change so lists can redraw.
 */
class ResumePositions(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** Saved position in ms, or 0. Keyed by the bare video id (as earlier versions stored it). */
    fun get(videoId: String): Long = prefs.getLong(videoId, 0L)
    fun isFinished(videoId: String): Boolean = prefs.getBoolean(DONE + videoId, false)
    fun lastPlayed(videoId: String): Long = prefs.getLong(AT + videoId, 0L)

    fun set(videoId: String, positionMs: Long) {
        prefs.edit().putLong(videoId, positionMs).putLong(AT + videoId, now()).remove(DONE + videoId).apply()
        _version.value++
    }

    fun finish(videoId: String) {
        prefs.edit().remove(videoId).putBoolean(DONE + videoId, true).putLong(AT + videoId, now()).apply()
        _version.value++
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val DONE = "done:"
        const val AT = "at:"
    }
}
