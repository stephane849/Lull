package app.lull.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import app.lull.data.Settings
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns `lull://video/<id>[?q=high|normal]` into a playable audio URL at the moment the
 * player or the downloader needs it. Stream URLs expire after a few hours, so they are
 * never stored in the queue, only cached here briefly.
 */
class StreamResolver(context: Context, private val settings: Settings) : ResolvingDataSource.Resolver {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>() // key includes "orig" so old dubbed picks aren't reused

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != SCHEME) return dataSpec
        val videoId = uri.lastPathSegment ?: throw IOException("Missing video id")
        val high = when (uri.getQueryParameter("q")) {
            "high" -> true
            "normal" -> false
            else -> if (connectivity?.isActiveNetworkMetered == true) settings.mobileHighQuality else settings.wifiHighQuality
        }
        return dataSpec.buildUpon()
            .setUri(audioUrl(videoId, high))
            .setKey(dataSpec.key ?: videoId)
            .build()
    }

    private fun audioUrl(videoId: String, high: Boolean): String {
        val key = "$videoId|$high|orig"
        cache[key]?.let { (url, expires) -> if (System.currentTimeMillis() < expires) return url }

        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        } catch (e: Exception) {
            throw IOException("Couldn't load song $videoId", e)
        }
        val all = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        if (all.isEmpty()) throw IOException("No audio stream for $videoId")
        // Many videos now carry AI-dubbed tracks in other languages (and audio description).
        // Always play the original; fall back to any non-descriptive track, then anything.
        val streams = all.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { all.filter { it.audioTrackType != AudioTrackType.DESCRIPTIVE } }
            .ifEmpty { all }

        val chosen = if (high) {
            streams.maxBy { it.averageBitrate }
        } else {
            streams.filter { it.averageBitrate in 1..NORMAL_MAX_KBPS }.maxByOrNull { it.averageBitrate }
                ?: streams.minBy { it.averageBitrate }
        }
        cache[key] = chosen.content to System.currentTimeMillis() + URL_TTL_MS
        return chosen.content
    }

    companion object {
        const val SCHEME = "lull"
        private const val NORMAL_MAX_KBPS = 140
        private const val URL_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
