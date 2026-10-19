package app.lull.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.lull.core.MusicCollection
import app.lull.core.Track

/** Media ids shared by the phone screens and the Android Auto browse tree. */
object MediaIds {
    const val ROOT = "root"
    const val DOWNLOADS = "downloads"
    const val PLAYLISTS = "playlists"
    const val LIKED = "liked"
    const val PODCASTS = "podcasts"
    private const val TRACK = "track:"
    private const val COLLECTION = "collection:"

    fun track(videoId: String) = TRACK + videoId
    fun collection(browseId: String) = COLLECTION + browseId
    fun videoIdOf(mediaId: String): String? = mediaId.takeIf { it.startsWith(TRACK) }?.removePrefix(TRACK)
    fun browseIdOf(mediaId: String): String? = mediaId.takeIf { it.startsWith(COLLECTION) }?.removePrefix(COLLECTION)
}

fun playbackUri(videoId: String, quality: String? = null) =
    ("${StreamResolver.SCHEME}://video/$videoId" + (quality?.let { "?q=$it" } ?: "")).toUri()

/** No artwork anywhere: the metadata carries text only, so nothing is fetched or decoded. */
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(MediaIds.track(videoId))
        .setUri(playbackUri(videoId))
        .setCustomCacheKey(videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { durationSec?.let { setDurationMs(it * 1000L) } }
                .build(),
        )
        .build()

/** Controllers drop a MediaItem's URI when sending it to the service; this puts it back. */
fun MediaItem.withPlaybackUri(): MediaItem {
    val videoId = MediaIds.videoIdOf(mediaId) ?: return this
    return buildUpon().setUri(playbackUri(videoId)).setCustomCacheKey(videoId).build()
}

fun MediaItem.toTrack(): Track? {
    val videoId = MediaIds.videoIdOf(mediaId) ?: return null
    return Track(
        videoId = videoId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        durationSec = mediaMetadata.durationMs?.let { (it / 1000).toInt() },
    )
}

fun folderItem(mediaId: String, title: String, type: Int, subtitle: String? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build(),
        )
        .build()

fun MusicCollection.toFolderItem(): MediaItem =
    folderItem(MediaIds.collection(browseId), title, MediaMetadata.MEDIA_TYPE_PLAYLIST, subtitle)
