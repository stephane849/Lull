package app.lull.ui

import app.lull.core.Track
import app.lull.data.ResumePositions
import app.lull.ui.components.formatLeft

/** Progress shown on a row: only for long items (classes, episodes), never for songs. */
fun progressOf(resume: ResumePositions, track: Track): String? {
    val dur = track.durationSec ?: return null
    if (dur < LONG_ITEM_SEC) return null
    if (resume.isFinished(track.videoId)) return "Played"
    val pos = resume.get(track.videoId)
    return if (pos > 0) formatLeft(dur * 1000L - pos) else null
}

/**
 * Where to pick up: the item played most recently, or the one after it if that was
 * finished. Null when nothing in the list has been started.
 */
fun continueIndex(resume: ResumePositions, tracks: List<Track>): Int? {
    // Only series of long items (classes, episodes); albums and song playlists just play.
    fun long(i: Int) = (tracks[i].durationSec ?: 0) >= LONG_ITEM_SEC
    val last = tracks.indices.filter(::long).maxByOrNull { resume.lastPlayed(tracks[it].videoId) } ?: return null
    if (resume.lastPlayed(tracks[last].videoId) == 0L) return null
    if (!resume.isFinished(tracks[last].videoId)) return last
    return (last + 1 until tracks.size).firstOrNull { long(it) && !resume.isFinished(tracks[it].videoId) }
}

const val LONG_ITEM_SEC = 600

