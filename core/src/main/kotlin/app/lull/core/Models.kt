package app.lull.core

/** A playable song. Lull never stores or shows artwork, so there is no thumbnail field. */
data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Int? = null,
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
}
