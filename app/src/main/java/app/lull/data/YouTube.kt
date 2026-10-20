package app.lull.data

import app.lull.core.MusicCollection
import app.lull.core.CollectionKind
import app.lull.core.Page
import app.lull.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePlaylistLinkHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.extractor.Page as NpPage

/**
 * All of YouTube, through NewPipe Extractor (the same library that already finds the audio
 * streams). Searches anonymously. Only regular videos of a minute or more: no Shorts or live.
 * If this stops working, bump NewPipeExtractor in app/build.gradle.kts.
 */
class YouTube {
    private val pages = ConcurrentHashMap<String, NpPage>()

    /** Recordings (as [Track]) and playlists (as [MusicCollection]), in YouTube's order. */
    suspend fun search(query: String, continuation: String?): Page<Any> = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, emptyList(), "")
        val result = if (continuation == null) {
            extractor.fetchPage()
            extractor.initialPage
        } else {
            val page = pages.remove(continuation) ?: return@withContext Page(emptyList(), null)
            extractor.getPage(page)
        }
        val items: List<Any> = result.items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> {
                    if (item.streamType != StreamType.VIDEO_STREAM || item.isShortFormContent || item.duration < MIN_SECONDS) return@mapNotNull null
                    val id = runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(item.url) }.getOrNull()
                        ?: return@mapNotNull null
                    Track(id, item.name, item.uploaderName.orEmpty(), item.duration.toInt())
                }
                is PlaylistInfoItem -> {
                    val id = runCatching { YoutubePlaylistLinkHandlerFactory.getInstance().getId(item.url) }.getOrNull()
                        ?: return@mapNotNull null
                    // Mixes and radio lists can't be browsed as playlists.
                    if (id.startsWith("RD")) return@mapNotNull null
                    MusicCollection(
                        "VL$id", item.name,
                        listOfNotNull("YouTube playlist", item.uploaderName, item.streamCount.takeIf { it > 0 }?.let { "$it items" }).joinToString(" · "),
                        CollectionKind.PLAYLIST,
                    )
                }
                else -> null
            }
        }
        val next = result.nextPage?.let { p -> UUID.randomUUID().toString().also { pages[it] = p } }
        Page(items, next)
    }

    suspend fun video(videoId: String): Track = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        Track(videoId, info.name, info.uploaderName.orEmpty(), info.duration.toInt().takeIf { it > 0 })
    }

    /** A YouTube playlist, opened through YouTube Music's browse (which accepts them as VL<id>). */
    suspend fun playlist(playlistId: String): MusicCollection = withContext(Dispatchers.IO) {
        val name = runCatching {
            PlaylistInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/playlist?list=$playlistId")
        }.getOrNull()
        MusicCollection("VL$playlistId", name?.name ?: "YouTube playlist", name?.uploaderName.orEmpty(), CollectionKind.PLAYLIST)
    }

    private companion object {
        const val MIN_SECONDS = 60L
    }
}
