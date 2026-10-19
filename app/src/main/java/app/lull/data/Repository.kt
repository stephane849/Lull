package app.lull.data

import app.lull.core.InnerTube
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.SearchFilter
import app.lull.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coroutine-friendly front for the blocking InnerTube client. */
class Repository(private val api: InnerTube) {

    val isSignedIn: Boolean get() = api.isSignedIn

    suspend fun searchTracks(query: String, continuation: String? = null, filter: SearchFilter = SearchFilter.SONGS): Page<Track> =
        io { api.searchTracks(query, continuation, filter) }

    suspend fun searchCollections(query: String, filter: SearchFilter, continuation: String? = null): Page<MusicCollection> =
        io { api.searchCollections(query, filter, continuation) }

    suspend fun libraryCollections(browseId: String, continuation: String? = null): Page<MusicCollection> =
        io { api.libraryCollections(browseId, continuation) }

    suspend fun collectionTracks(browseId: String, continuation: String? = null): Page<Track> =
        io { api.collectionTracks(browseId, continuation) }

    suspend fun radio(videoId: String): List<Track> = io { api.radio(videoId) }

    suspend fun setLiked(videoId: String, liked: Boolean) = io { api.setLiked(videoId, liked) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
