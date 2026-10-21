package app.lull.ui

import app.lull.core.Track
import app.lull.data.ResumePositions
import app.lull.ui.components.formatLeft

/** Progress shown on a row: only for long items (classes, episodes), never for songs. */
/** A track's length in seconds: from its list, or learned when it last played. */
fun lengthSec(resume: ResumePositions, track: Track): Int? =
    track.durationSec ?: (resume.lengthMs(track.videoId) / 1000).toInt().takeIf { it > 0 }

fun progressOf(resume: ResumePositions, track: Track): String? {
    val dur = lengthSec(resume, track) ?: return null
    if (dur < LONG_ITEM_SEC) return null
    if (resume.isFinished(track.videoId)) return "Played"
    val pos = resume.get(track.videoId)
    return if (pos > 0) formatLeft(dur * 1000L - pos) else null
}

/**
 * Where to pick up: the item played most recently, or the one after it if that was
 * finished. Null when nothing in the list has been started.
 */
fun continueIndex(resume: ResumePositions, tracks: List<Track>, assumeLong: Boolean = false): Int? {
    // Only series of long items (classes, episodes); albums and song playlists just play.
    // In a podcast, an episode of unknown length counts as long.
    fun long(i: Int) = (lengthSec(resume, tracks[i]) ?: if (assumeLong) LONG_ITEM_SEC else 0) >= LONG_ITEM_SEC
    val last = tracks.indices.filter(::long).maxByOrNull { resume.lastPlayed(tracks[it].videoId) } ?: return null
    if (resume.lastPlayed(tracks[last].videoId) == 0L) return null
    if (!resume.isFinished(tracks[last].videoId)) return last
    return (last + 1 until tracks.size).firstOrNull { long(it) && !resume.isFinished(tracks[it].videoId) }
}

const val LONG_ITEM_SEC = 600

