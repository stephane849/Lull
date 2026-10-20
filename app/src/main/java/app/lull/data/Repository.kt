package app.lull.data

import app.lull.core.CollectionPage
import app.lull.core.InnerTube
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.PlaylistTarget
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

    suspend fun collectionPage(browseId: String, continuation: String? = null): CollectionPage =
        io { api.collectionPage(browseId, continuation) }

    suspend fun setSaved(playlistId: String, saved: Boolean) = io { api.setSaved(playlistId, saved) }
    suspend fun playlistTargets(videoId: String): List<PlaylistTarget> = io { api.playlistTargets(videoId) }
    suspend fun addToPlaylist(playlistId: String, videoId: String) = io { api.addToPlaylist(playlistId, videoId) }
    suspend fun createPlaylist(title: String, videoIds: List<String>): String? = io { api.createPlaylist(title, videoIds) }

    suspend fun movePlaylistItem(playlistId: String, setVideoId: String, before: String?, last: String?) =
        io { api.movePlaylistItem(playlistId, setVideoId, before, last) }
    suspend fun removePlaylistItem(playlistId: String, setVideoId: String, videoId: String) =
        io { api.removePlaylistItem(playlistId, setVideoId, videoId) }
    suspend fun renamePlaylist(playlistId: String, name: String) = io { api.renamePlaylist(playlistId, name) }
    suspend fun deletePlaylist(playlistId: String) = io { api.deletePlaylist(playlistId) }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) = io { api.setSubscribed(channelId, subscribed) }

    suspend fun radio(videoId: String): List<Track> = io { api.radio(videoId) }

    suspend fun setLiked(videoId: String, liked: Boolean) = io { api.setLiked(videoId, liked) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
