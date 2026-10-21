package app.lull.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.lull.AppGraph
import app.lull.ui.components.formatDuration
import app.lull.ui.progressOf
import app.lull.ui.continueIndex
import app.lull.core.Track
import app.lull.core.Titles
import androidx.media3.session.CommandButton
import app.lull.core.CollectionKind
import app.lull.core.InnerTube
import app.lull.data.ResumePositions
import app.lull.download.Downloads
import app.lull.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single player. The phone screens, the notification, Bluetooth and Android Auto are all
 * controllers of this one session, so the car adds no second player or process.
 *
 * Car mode is deliberately lean for a phone with little RAM: a two-level, text-only browse
 * tree (Downloads, Playlists, Liked songs), at most 30 rows per folder, no artwork, and a
 * small playback buffer.
 */
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    private var sleepTask: Runnable? = null
    private lateinit var resume: ResumePositions
    private var currentVideoId: String? = null
    private val saveTask = object : Runnable {
        override fun run() {
            if (player.isPlaying) saveResumePosition()
            handler.postDelayed(this, RESUME_SAVE_MS)
        }
    }
    private val searchResults = HashMap<String, List<MediaItem>>()

    /** The last song list the car browsed, so tapping one song queues the rest of that list. */
    private var lastBrowsedList: List<MediaItem> = emptyList()

    override fun onCreate() {
        super.onCreate()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
            .build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(AppGraph.playerDataSourceFactory()))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        resume = AppGraph.resume
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateCarButtons()
                    val d = player.duration
                    if (d != C.TIME_UNSET) currentVideoId?.let { resume.setLength(it, d) }
                }
                if (playbackState == Player.STATE_ENDED) {
                    currentVideoId?.let(resume::finish)
                    // Mindful default: the queue simply ends. Radio only if the user turned it on.
                    if (AppGraph.settings.radioAfterQueue) startRadio()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveResumePosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Finished naturally: mark it played. Skipped away: the last periodic save stands.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) currentVideoId?.let(resume::finish)
                currentVideoId = mediaItem?.mediaId?.let(MediaIds::videoIdOf)
                // Pick up long listening where it was left.
                val saved = currentVideoId?.let(resume::get) ?: 0L
                if (saved > RESUME_MIN_MS) player.seekTo(saved)
            }
        })
        handler.postDelayed(saveTask, RESUME_SAVE_MS)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        sleepTask?.let(handler::removeCallbacks)
        handler.removeCallbacks(saveTask)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Only long items (over 10 minutes) are remembered, so songs always start from the top. */
    private fun saveResumePosition() {
        val id = currentVideoId ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < LONG_ITEM_MS) return
        val position = player.currentPosition
        if (position > duration - FINISHED_MARGIN_MS) resume.finish(id) else resume.set(id, position)
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTask?.let(handler::removeCallbacks)
        sleepTask = null
        if (minutes <= 0) return
        val task = Runnable {
            player.pause()
            sleepTask = null
        }
        sleepTask = task
        handler.postDelayed(task, minutes * 60_000L)
    }

    private fun startRadio() {
        val seed = player.currentMediaItem
            ?: player.mediaItemCount.takeIf { it > 0 }?.let { player.getMediaItemAt(it - 1) }
            ?: return
        val videoId = MediaIds.videoIdOf(seed.mediaId) ?: return
        scope.launch {
            val items = runCatching { AppGraph.repo.radio(videoId) }.getOrDefault(emptyList())
                .filter { it.videoId != videoId }
                .take(RADIO_SIZE)
                .map { it.toMediaItem() }
            if (items.isEmpty()) return@launch
            val start = player.mediaItemCount
            player.addMediaItems(items)
            player.seekTo(start, 0)
            player.prepare()
            player.play()
        }
    }

    private suspend fun children(parentId: String): List<MediaItem> {
        val repo = AppGraph.repo
        return when (parentId) {
            // Android Auto shows up to four browsable root items as tabs.
            MediaIds.ROOT -> listOf(
                folderItem(MediaIds.PICK_UP, "Pick up", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folderItem(MediaIds.DOWNLOADS, "Offline", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folderItem(MediaIds.PODCASTS, "Podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
                folderItem(MediaIds.PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            )
            MediaIds.PICK_UP -> pickUp()
            MediaIds.DOWNLOADS -> withContext(Dispatchers.IO) { Downloads.completed() }
                .take(CAR_PAGE).let(::carList)
            MediaIds.PLAYLISTS -> listOf(folderItem(MediaIds.LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_PLAYLIST)) +
                AppGraph.savedLocally.merge(InnerTube.LIBRARY_PLAYLISTS, repo.libraryCollections(InnerTube.LIBRARY_PLAYLISTS).items)
                    .filter { it.kind == CollectionKind.PLAYLIST && it.browseId != InnerTube.LIKED_SONGS }
                    .take(CAR_PAGE - 1).map { it.toFolderItem() }
            MediaIds.PODCASTS -> AppGraph.savedLocally.merge(InnerTube.LIBRARY_PODCASTS, repo.libraryCollections(InnerTube.LIBRARY_PODCASTS).items)
                .take(CAR_PAGE).map { it.toFolderItem() }
            MediaIds.LIKED -> repo.collectionTracks(InnerTube.LIKED_SONGS).items
                .take(CAR_PAGE).let(::carList)
            else -> {
                val browseId = MediaIds.browseIdOf(parentId) ?: return emptyList()
                repo.collectionTracks(browseId).items
                    .take(CAR_PAGE).let(::carList)
            }
        }
    }

    /** Rows for a song list; tapping one queues the rest of this list after it. */
    private fun carList(tracks: List<Track>): List<MediaItem> {
        lastBrowsedList = tracks.map { it.toMediaItem() }
        return tracks.map { it.toCarItem(resume) }
    }

    /**
     * The car's first tab: continue the series in progress, the part after it, then the
     * collections you played from recently.
     */
    private fun pickUp(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val series = AppGraph.recentSeries.last()
        val at = series?.let { continueIndex(resume, it.tracks, assumeLong = it.collection.kind == CollectionKind.PODCAST) }
        if (series != null && at != null) {
            val tracks = series.tracks
            val short = Titles.distinct(tracks.map { it.title })
            // Tapping either row queues the whole series from that part, with real titles.
            lastBrowsedList = tracks.map { it.toMediaItem() }
            val current = tracks[at]
            val left = progressOf(resume, current) ?: current.durationSec?.let { formatDuration(it) }
            out += current.toCarItem(
                resume,
                title = "Continue: ${short[at]}",
                subtitle = listOfNotNull(series.collection.title.takeIf { tracks.size > 1 } ?: series.collection.subtitle, left).joinToString(" · "),
            )
            tracks.getOrNull(at + 1)?.let { next ->
                out += next.toCarItem(
                    resume,
                    title = short[at + 1],
                    subtitle = listOfNotNull("Next in series", next.durationSec?.let { formatDuration(it) }).joinToString(" · "),
                )
            }
        }
        AppGraph.recentSeries.recentCollections()
            .filter { it.browseId.isNotEmpty() && (it.browseId != series?.collection?.browseId || at == null) }
            .take(4)
            .forEach { out += it.toFolderItem(group = "Recently played") }
        if (out.isEmpty()) out += folderItem(MediaIds.LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_PLAYLIST)
        return out
    }

    /** Back 15 s / ahead 30 s beside previous and next, for long items only. */
    private fun updateCarButtons() {
        val long = player.duration != C.TIME_UNSET && player.duration >= LONG_ITEM_MS
        val buttons = if (!long) ImmutableList.of() else ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                .setDisplayName("Back 15 seconds")
                .setSessionCommand(SessionCommand(CMD_BACK_15, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setDisplayName("Forward 30 seconds")
                .setSessionCommand(SessionCommand(CMD_FORWARD_30, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
        )
        session?.setMediaButtonPreferences(buttons)
    }

    private fun seekBy(deltaMs: Long) {
        var target = (player.currentPosition + deltaMs).coerceAtLeast(0)
        if (player.duration != C.TIME_UNSET) target = target.coerceAtMost((player.duration - 1_000).coerceAtLeast(0))
        player.seekTo(target)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_RADIO, Bundle.EMPTY))
                .add(SessionCommand(CMD_BACK_15, Bundle.EMPTY))
                .add(SessionCommand(CMD_FORWARD_30, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP -> setSleepTimer(args.getInt(ARG_MINUTES))
                CMD_RADIO -> startRadio()
                CMD_BACK_15 -> seekBy(-15_000)
                CMD_FORWARD_30 -> seekBy(30_000)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Ask Android Auto for plain text lists rather than artwork grids.
            val extras = bundleOf(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
            val rootParams = LibraryParams.Builder().setExtras(extras).build()
            val root = folderItem(MediaIds.ROOT, "Lull", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                LibraryResult.ofItemList(children(parentId), params)
            } catch (e: Exception) {
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val items = runCatching { AppGraph.repo.searchTracks(query).items }.getOrDefault(emptyList())
                    .take(CAR_SEARCH).map { it.toMediaItem() }
                searchResults[query] = items
                session.notifySearchResultChanged(browser, query, items.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = searchResults[query].orEmpty()
            lastBrowsedList = items
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withPlaybackUri() }.toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // The car sends one tapped song; queue the rest of the list it came from.
            if (controller.packageName != packageName && mediaItems.size == 1) {
                val index = lastBrowsedList.indexOfFirst { it.mediaId == mediaItems[0].mediaId }
                if (index >= 0) {
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            lastBrowsedList.map { it.withPlaybackUri() }, index, startPositionMs,
                        ),
                    )
                }
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems.map { it.withPlaybackUri() }, startIndex, startPositionMs),
            )
        }
    }

    companion object {
        const val CMD_SLEEP = "app.lull.SLEEP"
        const val CMD_RADIO = "app.lull.RADIO"
        const val CMD_BACK_15 = "app.lull.BACK_15"
        const val CMD_FORWARD_30 = "app.lull.FORWARD_30"
        const val ARG_MINUTES = "minutes"
        private const val CAR_PAGE = 30
        private const val CAR_SEARCH = 10
        private const val RADIO_SIZE = 25
        private const val RESUME_SAVE_MS = 15_000L
        private const val RESUME_MIN_MS = 30_000L
        private const val LONG_ITEM_MS = 10 * 60_000L
        private const val FINISHED_MARGIN_MS = 60_000L
    }
}
