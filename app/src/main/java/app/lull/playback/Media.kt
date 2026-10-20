package app.lull.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.lull.core.MusicCollection
import app.lull.core.Track
import app.lull.data.ResumePositions

/** Media ids shared by the phone screens and the Android Auto browse tree. */
object MediaIds {
    const val ROOT = "root"
    const val DOWNLOADS = "downloads"
    const val PLAYLISTS = "playlists"
    const val LIKED = "liked"
    const val PODCASTS = "podcasts"
    const val PICK_UP = "pickup"
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

// Android Auto extras, as plain keys (stable across Media3 versions).
private const val PLAYBACK_STATUS = "android.media.extra.PLAYBACK_STATUS"
private const val COMPLETION_PERCENTAGE = "androidx.media.MediaItem.Extras.COMPLETION_PERCENTAGE"
private const val GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
private const val NOT_PLAYED = 0
private const val PARTLY_PLAYED = 1
private const val PLAYED = 2
private const val LONG_SEC = 600

/**
 * A row for the car. Long items carry a played / part-played mark; [title] and [subtitle]
 * override what the row says (the queue still uses the real track).
 */
fun Track.toCarItem(
    resume: ResumePositions,
    title: String = this.title,
    subtitle: String? = null,
    group: String? = null,
): MediaItem {
    val extras = Bundle()
    val dur = durationSec ?: 0
    if (dur >= LONG_SEC) {
        val pos = resume.get(videoId)
        when {
            resume.isFinished(videoId) -> extras.putInt(PLAYBACK_STATUS, PLAYED)
            pos > 0 -> {
                extras.putInt(PLAYBACK_STATUS, PARTLY_PLAYED)
                extras.putDouble(COMPLETION_PERCENTAGE, (pos / (dur * 1000.0)).coerceIn(0.0, 1.0))
            }
            else -> extras.putInt(PLAYBACK_STATUS, NOT_PLAYED)
        }
    }
    group?.let { extras.putString(GROUP_TITLE, it) }
    val base = toMediaItem()
    return base.buildUpon()
        .setMediaMetadata(
            base.mediaMetadata.buildUpon()
                .setTitle(title)
                .apply { if (subtitle != null) { setArtist(subtitle); setSubtitle(subtitle) } }
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MusicCollection.toFolderItem(group: String?): MediaItem {
    val base = toFolderItem()
    if (group == null) return base
    return base.buildUpon()
        .setMediaMetadata(base.mediaMetadata.buildUpon().setExtras(Bundle().apply { putString(GROUP_TITLE, group) }).build())
        .build()
}
