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
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.SearchFilter
import app.lull.core.Track
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

    val pageItems: List<T> get() = items.drop(page * pageSize).take(pageSize)
    val hasPrevious: Boolean get() = page > 0
    val hasNext: Boolean get() = (page + 1) * pageSize < items.size || !exhausted
    val pageLabel: String
        get() {
            val pages = maxOf(1, (items.size + pageSize - 1) / pageSize)
            return "Page ${page + 1} of $pages" + if (exhausted) "" else "+"
        }

    fun start() {
        items = emptyList(); page = 0; continuation = null; exhausted = false; started = true
        fetch()
    }

    fun next() {
        if ((page + 1) * pageSize < items.size) page++
        else fetch { if ((page + 1) * pageSize < items.size) page++ }
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
                val result = load(continuation)
                val before = items.size
                items = (items + result.items).distinct()
                continuation = result.continuation
                exhausted = continuation == null || items.size == before
                after()
            } catch (e: Exception) {
                error = if (e is InnerTubeException && e.isAuthError) {
                    "Your session expired. Sign in again in Settings."
                } else {
                    "Couldn't load. Check your connection and try again."
                }
            } finally {
                loading = false
            }
        }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppGraph.repo
    val player = PlayerConnection(app, viewModelScope)

    // --- navigation ---------------------------------------------------------------------
    val stack = mutableStateListOf<Screen>(Screen.Start)
    val screen: Screen get() = stack.last()
    fun go(screen: Screen) { stack.add(screen) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false

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
        Paged(viewModelScope) { cont -> repo.libraryCollections(browseId, cont) }
    }

    private val collections = HashMap<String, Paged<Track>>()
    fun collectionTracks(browseId: String): Paged<Track> = collections.getOrPut(browseId) {
        Paged(viewModelScope) { cont -> repo.collectionTracks(browseId, cont) }
    }

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
