package app.lull.ui

import app.lull.core.Titles
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import app.lull.core.Track
import app.lull.playback.MediaIds
import app.lull.playback.PlaybackService
import app.lull.playback.toMediaItem
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(val title: String, val artist: String)

data class PlayerUi(
    val hasMedia: Boolean = false,
    val videoId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val index: Int = 0,
    val queue: List<QueueEntry> = emptyList(),
    val source: String? = null,
    val speed: Float = 1f,
) {
    val upcoming: List<QueueEntry> get() = queue.drop(index + 1)
}

/**
 * The screens' handle on the playback service. Position is refreshed only every 15 seconds
 * (or on tap) so the E Ink screen isn't redrawn every second.
 */
class PlayerConnection(context: Context, scope: CoroutineScope) {

    private var controller: MediaController? = null
    private var source: String? = null
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let {
                controller = it
                it.addListener(listener)
                refresh()
            }
        }, MoreExecutors.directExecutor())

        scope.launch {
            while (isActive) {
                delay(POSITION_REFRESH_MS)
                if (controller?.isPlaying == true) refresh()
            }
        }
    }

    fun refresh() {
        val c = controller ?: return
        val current = c.currentMediaItem
        _state.value = PlayerUi(
            hasMedia = current != null,
            videoId = current?.let { MediaIds.videoIdOf(it.mediaId) },
            title = current?.mediaMetadata?.title?.toString().orEmpty(),
            artist = current?.mediaMetadata?.artist?.toString().orEmpty().takeUnless(Titles::isCount).orEmpty(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0),
            index = c.currentMediaItemIndex,
            queue = (0 until c.mediaItemCount).map { i ->
                val m = c.getMediaItemAt(i).mediaMetadata
                QueueEntry(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty().takeUnless(Titles::isCount).orEmpty())
            },
            source = source,
            speed = c.playbackParameters.speed,
        )
    }

    /** 1× → 1.25× → 1.5× → 1.75× → 2× → 0.75× → 1×. Handy for classes and talks. */
    fun cycleSpeed() {
        val c = controller ?: return
        val current = c.playbackParameters.speed
        val next = SPEEDS.firstOrNull { it > current + 0.01f } ?: SPEEDS.first()
        c.setPlaybackSpeed(next)
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, from: String? = null) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        source = from
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0)
        c.prepare()
        c.play()
    }

    fun shuffle(tracks: List<Track>, from: String? = null) = play(tracks.shuffled(), 0, from)

    /** Right after the current item. */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return addToQueue(track)
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        c.addMediaItem(track.toMediaItem())
        if (c.mediaItemCount == 1) {
            c.prepare()
            c.play()
        }
    }

    fun toggle() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(c.currentMediaItemIndex, 0)
                c.play()
            }
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    /** Jump back or ahead within the current item (−15 s / +30 s for talks). */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        var target = (c.currentPosition + deltaMs).coerceAtLeast(0)
        val duration = c.duration
        if (duration != C.TIME_UNSET && duration > 0) target = target.coerceAtMost(duration - 1_000)
        c.seekTo(target)
        refresh()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPrevious()
    fun removeAt(index: Int) = controller?.removeMediaItem(index)

    fun clearUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY),
            bundleOf(PlaybackService.ARG_MINUTES to minutes),
        )
    }

    fun startRadio() {
        controller?.sendCustomCommand(SessionCommand(PlaybackService.CMD_RADIO, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private companion object {
        const val POSITION_REFRESH_MS = 15_000L
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
