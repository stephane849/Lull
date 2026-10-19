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
import app.lull.core.CollectionKind
import app.lull.core.InnerTube
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
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Mindful default: the queue simply ends. Radio only if the user turned it on.
                if (playbackState == Player.STATE_ENDED && AppGraph.settings.radioAfterQueue) startRadio()
            }
        })

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
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
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
            MediaIds.ROOT -> listOf(
                folderItem(MediaIds.DOWNLOADS, "Downloads", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folderItem(MediaIds.PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                folderItem(MediaIds.LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_PLAYLIST),
            )
            MediaIds.DOWNLOADS -> withContext(Dispatchers.IO) { Downloads.completed() }
                .take(CAR_PAGE).map { it.toMediaItem() }.also { lastBrowsedList = it }
            MediaIds.PLAYLISTS -> repo.libraryCollections(InnerTube.LIBRARY_PLAYLISTS).items
                .filter { it.kind == CollectionKind.PLAYLIST }
                .take(CAR_PAGE).map { it.toFolderItem() }
            MediaIds.LIKED -> repo.collectionTracks(InnerTube.LIKED_SONGS).items
                .take(CAR_PAGE).map { it.toMediaItem() }.also { lastBrowsedList = it }
            else -> {
                val browseId = MediaIds.browseIdOf(parentId) ?: return emptyList()
                repo.collectionTracks(browseId).items
                    .take(CAR_PAGE).map { it.toMediaItem() }.also { lastBrowsedList = it }
            }
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_RADIO, Bundle.EMPTY))
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
        const val ARG_MINUTES = "minutes"
        private const val CAR_PAGE = 30
        private const val CAR_SEARCH = 10
        private const val RADIO_SIZE = 25
    }
}
