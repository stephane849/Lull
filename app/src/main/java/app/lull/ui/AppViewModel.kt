package app.lull.ui

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lull.AppGraph
import app.lull.core.CollectionKind
import app.lull.core.InnerTube
import app.lull.core.InnerTubeException
import app.lull.core.LibraryState
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.PlaylistTarget
import app.lull.core.SearchFilter
import app.lull.core.Subscription
import app.lull.core.Track
import app.lull.data.Series
import app.lull.download.Downloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Start : Screen
    data object Search : Screen
    data object Library : Screen
    data object Downloads : Screen
    data class LibraryShelf(val title: String, val browseId: String) : Screen
    data class Collection(val collection: MusicCollection) : Screen
    data object NowPlaying : Screen
    data object Queue : Screen
    data object Settings : Screen
    data object SignIn : Screen
    data class TrackActions(val track: Track) : Screen
    data class AddToPlaylist(val track: Track) : Screen
    data class EditPlaylist(val collection: MusicCollection) : Screen
    data class CollectionActions(val collection: MusicCollection) : Screen
}

/**
 * Fixed-size pages with Previous / Next instead of endless scrolling. Server pages are
 * fetched only when you page past what is already loaded.
 */
class Paged<T>(
    private val scope: CoroutineScope,
    val pageSize: Int = 6,
    private val load: suspend (continuation: String?) -> Page<T>,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var page by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var started by mutableStateOf(false)
        private set
    private var continuation: String? = null
    /** True once the server has no more pages, so the item count is final. */
    var exhausted by mutableStateOf(false)
        private set

    /** Shown oldest-first: the whole list is loaded (up to [MAX_ALL]) and paged in reverse. */
    var reversed by mutableStateOf(false)
        private set
    private var capped by mutableStateOf(false)

    /** True once every item that will be shown is here, so counts and reversed order are final. */
    val complete: Boolean get() = exhausted || capped
    val ordered: List<T> get() = if (reversed) items.asReversed() else items

    val pageItems: List<T> get() = ordered.drop(page * pageSize).take(pageSize)
    val hasPrevious: Boolean get() = page > 0
    val hasNext: Boolean get() = (page + 1) * pageSize < items.size || (!reversed && !exhausted)
    val pageLabel: String
        get() {
            val pages = maxOf(1, (items.size + pageSize - 1) / pageSize)
            return "Page ${page + 1} of $pages" + if (complete || reversed) "" else "+"
        }

    fun start() {
        items = emptyList(); page = 0; continuation = null; exhausted = false; capped = false; started = true
        fetch()
    }

    private var wantAll = false

    /** Loads every item (up to [MAX_ALL]), for editing. */
    fun loadEverything() {
        wantAll = true
        if (!started) start() else loadAll()
    }

    fun setOrder(reverse: Boolean) {
        if (reversed == reverse) return
        reversed = reverse
        page = 0
        if (reverse && started) loadAll()
    }

    fun next() {
        if ((page + 1) * pageSize < items.size) page++
        else if (!reversed) fetch { if ((page + 1) * pageSize < items.size) page++ }
    }

    fun previous() {
        if (page > 0) page--
    }

    private fun fetch(after: () -> Unit = {}) {
        if (loading || exhausted) return
        loading = true
        error = null
        scope.launch {
            try {
                if (fetchOnce()) after()
            } finally {
                loading = false
            }
            if ((reversed || wantAll) && !complete && error == null) loadAll()
        }
    }

    /** Keeps fetching pages until the list ends (or [MAX_ALL]), for oldest-first order. */
    private fun loadAll() {
        if (loading || complete) return
        loading = true
        error = null
        scope.launch {
            try {
                while (!exhausted) {
                    if (items.size >= MAX_ALL) { capped = true; break }
                    if (!fetchOnce()) break
                }
            } finally {
                loading = false
            }
        }
    }

    private suspend fun fetchOnce(): Boolean = try {
        val result = load(continuation)
        val before = items.size
        items = (items + result.items).distinct()
        continuation = result.continuation
        exhausted = continuation == null || items.size == before
        true
    } catch (e: Exception) {
        error = if (e is InnerTubeException && e.isAuthError) {
            "Your session expired. Sign in again in Settings."
        } else {
            "Couldn't load. Check your connection and try again."
        }
        false
    }

    private companion object {
        const val MAX_ALL = 500
    }
}

private val LIBRARY_SHELVES = listOf(InnerTube.LIBRARY_ALBUMS, InnerTube.LIBRARY_PODCASTS, InnerTube.LIBRARY_PLAYLISTS)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppGraph.repo
    val player = PlayerConnection(app, viewModelScope)

    // --- navigation ---------------------------------------------------------------------
    val stack = mutableStateListOf<Screen>(Screen.Start)
    val screen: Screen get() = stack.last()
    fun go(screen: Screen) { stack.add(screen) }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        // Leaving the editor (Done or the system back): the playlist page reloads its new order.
        (stack.last() as? Screen.EditPlaylist)?.let { collections.remove(it.collection.browseId) }
        stack.removeAt(stack.lastIndex)
        return true
    }

    // --- account ------------------------------------------------------------------------
    var signedIn by mutableStateOf(repo.isSignedIn)
        private set

    fun onSignedIn(cookie: String) {
        AppGraph.credentials.save(cookie)
        signedIn = true
        stack.clear()
        stack.add(Screen.Start)
    }

    fun signOut() {
        AppGraph.credentials.clear()
        CookieManager.getInstance().removeAllCookies(null)
        signedIn = false
    }

    // --- search -------------------------------------------------------------------------
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.SONGS)
        private set
    val searchResults = Paged<Any>(viewModelScope) { cont ->
        val q = query.trim()
        if (filter == SearchFilter.SONGS || filter == SearchFilter.EPISODES) {
            repo.searchTracks(q, cont, filter).let { Page(it.items, it.continuation) }
        }
        else repo.searchCollections(q, filter, cont).let { Page(it.items, it.continuation) }
    }

    fun search() { if (query.isNotBlank()) searchResults.start() }
    fun setSearchFilter(f: SearchFilter) { filter = f; search() }

    // --- library ------------------------------------------------------------------------
    private val shelves = HashMap<String, Paged<MusicCollection>>()
    fun shelf(browseId: String): Paged<MusicCollection> = shelves.getOrPut(browseId) {
        Paged(viewModelScope) { cont ->
            val page = repo.libraryCollections(browseId, cont)
            // Your Lull-saved items go on top of the first page if the server left them out.
            if (cont == null) Page(AppGraph.savedLocally.merge(browseId, page.items), page.continuation)
            else Page(page.items.filter { c -> AppGraph.savedLocally.all().none { it.browseId == c.browseId } }, page.continuation)
        }
    }

    private val collections = HashMap<String, Paged<Track>>()
    fun collectionTracks(browseId: String): Paged<Track> = collections.getOrPut(browseId) {
        Paged<Track>(viewModelScope) { cont ->
            val page = repo.collectionPage(browseId, cont)
            page.library?.let { libraryStates[browseId] = it }
            if (cont == null && page.editable) editable.add(browseId)
            page.subscription?.let { subscriptions[browseId] = it }
            page.tracks
        }.apply { setOrder(AppGraph.settings.isReversed(browseId)) }
    }

    // --- editing your playlists --------------------------------------------------------------
    /** Browse ids of playlists you own (seen with an editable header). */
    val editable = androidx.compose.runtime.mutableStateListOf<String>()

    /** The working copy of the playlist being edited, in server order. */
    val editItems = androidx.compose.runtime.mutableStateListOf<Track>()
    var editTitle by mutableStateOf("")
        private set
    var editError by mutableStateOf<String?>(null)
        private set
    var editBusy by mutableStateOf(false)
        private set
    private var editingId: String? = null

    fun startEditing(collection: MusicCollection) {
        editingId = collection.browseId
        editTitle = collection.title
        editError = null
        editItems.clear()
        collectionTracks(collection.browseId).loadEverything()
    }

    /** Called as pages arrive; fills the working copy once the whole list is in. */
    fun syncEditItems(paged: Paged<Track>) {
        if (paged.complete && editItems.isEmpty() && paged.items.isNotEmpty()) editItems.addAll(paged.items)
    }

    /** Liked songs can't be reordered, only unliked. */
    val editingLiked: Boolean get() = editingId == InnerTube.LIKED_SONGS

    val canEditItems: Boolean get() = editItems.isNotEmpty() && (editingLiked || editItems.all { it.setVideoId != null })

    fun moveItem(from: Int, to: Int) {
        val id = editingId ?: return
        if (from == to || from !in editItems.indices || to !in editItems.indices) return
        val before = editItems.toList()
        val moved = editItems.removeAt(from)
        editItems.add(to, moved)
        val successor = editItems.getOrNull(to + 1)?.setVideoId
        val last = editItems.getOrNull(editItems.lastIndex - 1)?.setVideoId
        editRequest(before) { repo.movePlaylistItem(id, moved.setVideoId!!, successor, if (successor == null) last else null) }
    }

    fun removeItem(index: Int) {
        val id = editingId ?: return
        if (index !in editItems.indices) return
        val before = editItems.toList()
        val t = editItems.removeAt(index)
        if (editingLiked) {
            liked[t.videoId] = false
            editRequest(before) { repo.setLiked(t.videoId, false) }
        } else {
            editRequest(before) { repo.removePlaylistItem(id, t.setVideoId!!, t.videoId) }
        }
    }

    fun renameEditing(name: String) {
        val id = editingId ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean == editTitle) return
        val old = editTitle
        editTitle = clean
        viewModelScope.launch {
            runCatching { repo.renamePlaylist(id, clean) }
                .onSuccess { renameInStack(id, clean); shelves.remove(InnerTube.LIBRARY_PLAYLISTS) }
                .onFailure { editTitle = old; editError = "Couldn't rename. Try again." }
        }
    }

    fun deleteEditing() {
        val id = editingId ?: return
        deletePlaylist(id) { ok -> if (!ok) editError = "Couldn't delete the playlist." }
    }

    /** Deletes one of your playlists and leaves any screens showing it. */
    fun deletePlaylist(browseId: String, done: (Boolean) -> Unit) {
        editBusy = true
        viewModelScope.launch {
            val ok = runCatching { repo.deletePlaylist(browseId) }.isSuccess
            if (ok) {
                shelves.remove(InnerTube.LIBRARY_PLAYLISTS)
                collections.remove(browseId)
                editable.remove(browseId)
                stack.removeAll {
                    it is Screen.EditPlaylist || it is Screen.CollectionActions ||
                        (it is Screen.Collection && it.collection.browseId == browseId)
                }
                if (stack.isEmpty()) stack.add(Screen.Start)
            }
            editBusy = false
            done(ok)
        }
    }

    /** Done: the playlist page reloads with the new order. */
    fun finishEditing() {
        editingId = null
        back()
    }

    private fun editRequest(before: List<Track>, call: suspend () -> Unit) {
        editError = null
        viewModelScope.launch {
            runCatching { call() }.onFailure {
                editItems.clear(); editItems.addAll(before)
                editError = "Couldn't save that change. It's been undone."
            }
        }
    }

    private fun renameInStack(browseId: String, title: String) {
        for (i in stack.indices) {
            val sc = stack[i]
            if (sc is Screen.Collection && sc.collection.browseId == browseId) stack[i] = Screen.Collection(sc.collection.copy(title = title))
            if (sc is Screen.EditPlaylist && sc.collection.browseId == browseId) stack[i] = Screen.EditPlaylist(sc.collection.copy(title = title))
        }
    }

    // --- saving to library ----------------------------------------------------------------
    /** Save state per collection browse id, read from its page. Absent: no Save button. */
    val libraryStates = mutableStateMapOf<String, LibraryState>()
    var libraryError by mutableStateOf<String?>(null)
        private set

    fun toggleSaved(collection: MusicCollection) {
        val browseId = collection.browseId
        val state = libraryStates[browseId] ?: return
        val now = !state.saved
        libraryStates[browseId] = state.copy(saved = now)
        libraryError = null
        viewModelScope.launch {
            runCatching { repo.setSaved(state.playlistId, now) }
                .onSuccess {
                    AppGraph.savedLocally.set(collection, now)
                    LIBRARY_SHELVES.forEach { shelves.remove(it) }
                }
                .onFailure {
                    libraryStates[browseId] = state
                    libraryError = "Couldn't update your library. Try again."
                }
        }
    }

    /** Artist pages: whether you're subscribed. */
    val subscriptions = mutableStateMapOf<String, Subscription>()

    fun toggleSubscribed(browseId: String) {
        val sub = subscriptions[browseId] ?: return
        val now = !sub.subscribed
        subscriptions[browseId] = sub.copy(subscribed = now)
        libraryError = null
        viewModelScope.launch {
            runCatching { repo.setSubscribed(sub.channelId, now) }
                .onSuccess { shelves.remove(InnerTube.LIBRARY_ARTISTS) }
                .onFailure {
                    subscriptions[browseId] = sub
                    libraryError = "Couldn't update your subscriptions. Try again."
                }
        }
    }

    // --- add to playlist ------------------------------------------------------------------
    var playlistTargets by mutableStateOf<List<PlaylistTarget>?>(null)
        private set
    var playlistStatus by mutableStateOf<String?>(null)
        private set
    var playlistBusy by mutableStateOf(false)
        private set

    fun loadPlaylistTargets(videoId: String) {
        playlistTargets = null
        playlistStatus = null
        viewModelScope.launch {
            runCatching { repo.playlistTargets(videoId) }
                .onSuccess { playlistTargets = it }
                .onFailure { playlistTargets = emptyList(); playlistStatus = "Couldn't load your playlists." }
        }
    }

    fun addToPlaylist(target: PlaylistTarget, track: Track) {
        if (playlistBusy) return
        playlistBusy = true
        playlistStatus = null
        viewModelScope.launch {
            playlistStatus = runCatching { repo.addToPlaylist(target.playlistId, track.videoId) }
                .fold({ "Added to ${target.title}." }, { "Couldn't add to ${target.title}." })
            playlistBusy = false
            shelves.remove(InnerTube.LIBRARY_PLAYLISTS)
            collections.remove("VL" + target.playlistId.removePrefix("VL"))
        }
    }

    fun createPlaylist(title: String, track: Track) {
        val name = title.trim()
        if (name.isEmpty() || playlistBusy) return
        playlistBusy = true
        playlistStatus = null
        viewModelScope.launch {
            playlistStatus = runCatching { repo.createPlaylist(name, listOf(track.videoId)) }
                .fold({ if (it != null) "Made “$name” with this song." else "Couldn't make the playlist." },
                    { "Couldn't make the playlist." })
            playlistBusy = false
            shelves.remove(InnerTube.LIBRARY_PLAYLISTS)
        }
    }

    // --- recent series (home screen) ------------------------------------------------------
    var lastSeries by mutableStateOf<Series?>(AppGraph.recentSeries.last())
        private set

    /** Plays from a collection page; lists of long items are remembered for "Pick up". */
    fun playFrom(collection: MusicCollection, tracks: List<Track>, index: Int, shuffle: Boolean = false) {
        if (collection.browseId != InnerTube.LIKED_SONGS) AppGraph.recentSeries.touch(collection)
        if (tracks.any { (it.durationSec ?: 0) >= LONG_ITEM_SEC }) {
            AppGraph.recentSeries.save(collection, tracks)
            lastSeries = Series(collection, tracks.take(300))
        }
        if (shuffle) player.shuffle(tracks, collection.title) else player.play(tracks, index, collection.title)
    }

    /** Oldest-first for podcasts and playlists, remembered per list. */
    fun setReversed(browseId: String, reversed: Boolean) {
        AppGraph.settings.setReversed(browseId, reversed)
        collectionTracks(browseId).setOrder(reversed)
    }

    // --- listening progress ---------------------------------------------------------------
    val resume get() = AppGraph.resume

    fun likedSongs() = MusicCollection(InnerTube.LIKED_SONGS, "Liked songs", "Your liked songs", CollectionKind.PLAYLIST)

    // --- downloads ----------------------------------------------------------------------
    var downloaded by mutableStateOf<Set<String>>(emptySet())
        private set
    var downloading by mutableStateOf<Set<String>>(emptySet())
        private set
    var downloadedTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var bytesUsed by mutableStateOf(0L)
        private set

    init {
        viewModelScope.launch {
            AppGraph.downloadsVersion.collect {
                val (done, pending) = withContext(Dispatchers.IO) { Downloads.states() }
                downloaded = done
                downloading = pending
                downloadedTracks = withContext(Dispatchers.IO) { Downloads.completed() }
                bytesUsed = withContext(Dispatchers.IO) { Downloads.bytesUsed() }
            }
        }
    }

    fun download(track: Track) = Downloads.add(getApplication(), track)
    fun downloadAll(tracks: List<Track>) =
        Downloads.addAll(getApplication(), tracks.filter { it.videoId !in downloaded && it.videoId !in downloading })
    fun removeDownload(videoId: String) = Downloads.remove(getApplication(), videoId)

    fun setDownloadWifiOnly(on: Boolean) {
        AppGraph.settings.downloadWifiOnly = on
        Downloads.setWifiOnly(getApplication(), on)
    }

    // --- likes and sleep ----------------------------------------------------------------
    val liked = mutableStateMapOf<String, Boolean>()

    fun toggleLike(videoId: String) {
        val now = !(liked[videoId] ?: false)
        liked[videoId] = now
        viewModelScope.launch {
            runCatching { repo.setLiked(videoId, now) }.onFailure { liked[videoId] = !now }
        }
    }

    var sleepMinutes by mutableIntStateOf(0)
        private set

    /** Off → 15 → 30 → 60 → Off. */
    fun cycleSleepTimer() {
        sleepMinutes = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
        player.setSleepTimer(sleepMinutes)
    }

    override fun onCleared() {
        player.release()
    }
}
