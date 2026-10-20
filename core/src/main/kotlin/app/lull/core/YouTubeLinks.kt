package app.lull.core

/** A YouTube or YouTube Music link shared into Lull. */
sealed interface SharedLink {
    data class Video(val videoId: String) : SharedLink
    data class Playlist(val playlistId: String) : SharedLink
}

/** Finds the first YouTube video or playlist link in shared text ("Check this out: https://youtu.be/…"). */
object YouTubeLinks {
    private val URL = Regex("""https?://\S+""")
    private val ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val LIST = Regex("""^[A-Za-z0-9_-]{10,}$""")

    fun parse(text: String): SharedLink? {
        for (m in URL.findAll(text)) {
            val url = m.value.trimEnd('.', ',', ')', ']', '>', '"', '\'')
            val noScheme = url.substringAfter("://")
            val host = noScheme.substringBefore('/').substringBefore('?').lowercase().removePrefix("www.").removePrefix("m.")
            if (host != "youtu.be" && !host.endsWith("youtube.com")) continue
            val path = noScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
            val query = noScheme.substringAfter('?', "").substringBefore('#').split('&')
                .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()

            // A video wins over the playlist it was shared from.
            val video = when {
                host == "youtu.be" -> path.substringBefore('/')
                path == "watch" -> query["v"]
                path.startsWith("shorts/") || path.startsWith("live/") || path.startsWith("embed/") -> path.substringAfter('/').substringBefore('/')
                else -> null
            }
            if (video != null && ID.matches(video)) return SharedLink.Video(video)
            val list = query["list"]
            if (list != null && LIST.matches(list)) return SharedLink.Playlist(list)
        }
        return null
    }
}
