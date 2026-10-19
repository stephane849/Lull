package app.lull.download

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.lull.AppGraph
import app.lull.core.Track
import app.lull.playback.playbackUri
import org.json.JSONObject

/**
 * Offline downloads (YouTube Music Premium). Audio is stored in app-private storage through
 * Media3's download manager; the song's title and artist ride along in the request data,
 * so no separate database is needed. Blocking reads: call off the main thread.
 */
object Downloads {

    fun add(context: Context, track: Track) {
        val quality = if (AppGraph.settings.downloadHighQuality) "high" else "normal"
        val request = DownloadRequest.Builder(track.videoId, playbackUri(track.videoId, quality))
            .setCustomCacheKey(track.videoId)
            .setData(encode(track))
            .build()
        DownloadService.sendAddDownload(context, LullDownloadService::class.java, request, false)
    }

    fun addAll(context: Context, tracks: List<Track>) = tracks.forEach { add(context, it) }

    fun remove(context: Context, videoId: String) =
        DownloadService.sendRemoveDownload(context, LullDownloadService::class.java, videoId, false)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) =
        DownloadService.sendSetRequirements(
            context, LullDownloadService::class.java, AppGraph.requirementsFor(wifiOnly), false,
        )

    /** Finished downloads, newest first. */
    fun completed(): List<Track> = read(Download.STATE_COMPLETED)
        .sortedByDescending { it.updateTimeMs }
        .mapNotNull { decode(it.request.data) }

    /** Ids that are finished, and ids still queued or downloading. */
    fun states(): Pair<Set<String>, Set<String>> {
        val all = read(Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING)
        val done = all.filter { it.state == Download.STATE_COMPLETED }.map { it.request.id }.toSet()
        val pending = all.filter { it.state != Download.STATE_COMPLETED }.map { it.request.id }.toSet()
        return done to pending
    }

    fun bytesUsed(): Long = AppGraph.downloadCache.cacheSpace

    private fun read(vararg states: Int): List<Download> {
        val out = ArrayList<Download>()
        AppGraph.downloadManager.downloadIndex.getDownloads(*states).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.download)
        }
        return out
    }

    private fun encode(track: Track): ByteArray = JSONObject()
        .put("id", track.videoId)
        .put("title", track.title)
        .put("artist", track.artist)
        .put("duration", track.durationSec ?: -1)
        .toString().toByteArray()

    private fun decode(data: ByteArray): Track? = runCatching {
        val o = JSONObject(String(data))
        Track(o.getString("id"), o.getString("title"), o.getString("artist"), o.getInt("duration").takeIf { it >= 0 })
    }.getOrNull()
}
