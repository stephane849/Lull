package app.lull.core

/** A playable song. Lull never stores or shows artwork, so there is no thumbnail field. */
data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Int? = null,
    /** This entry's id inside one of your playlists; needed to move or remove it there. */
    val setVideoId: String? = null,
)

enum class CollectionKind { PLAYLIST, ALBUM, ARTIST, PODCAST, OTHER }

/** A browsable thing: a playlist, album or artist. */
data class MusicCollection(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val kind: CollectionKind,
)

/** One server page of results plus the token for the next one (null when there is none). */
data class Page<T>(val items: List<T>, val continuation: String?)

/**
 * YouTube Music search filters. These `params` strings are the ones the web client sends;
 * if Google changes them, search still works but stops filtering. Update them here only.
 */
enum class SearchFilter(val params: String) {
    SONGS("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    ALBUMS("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    PLAYLISTS("EgeKAQQoAEABagoQAxAEEAoQCRAF"),
    PODCASTS("EgWKAQJQAWoMEA4QChADEAQQCRAF"),
    EPISODES("EgWKAQJIAWoMEA4QChADEAQQCRAF"),
    /** All of YouTube, searched through NewPipe rather than YouTube Music. */
    YOUTUBE(""),
}

/** Whether a playlist, album or podcast is in your library, and the id used to change that. */
data class LibraryState(val playlistId: String, val saved: Boolean)

/** A playlist you can add songs to. */
data class PlaylistTarget(val playlistId: String, val title: String)

/**
 * One page of a collection's tracks. [library] and [editable] are read from the first page
 * only: [editable] is true for playlists you own.
 */
data class CollectionPage(
    val tracks: Page<Track>,
    val library: LibraryState?,
    val editable: Boolean = false,
    val subscription: Subscription? = null,
)

/** An artist page's Subscribe button: the channel and whether you follow it. */
data class Subscription(val channelId: String, val subscribed: Boolean)

/** One block of an unfiltered YouTube Music search ("Songs", "Episodes", "Albums"…). */
data class SearchSection(val title: String, val tracks: List<Track>, val collections: List<MusicCollection>)
