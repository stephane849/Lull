package app.lull.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeLinksTest {
    @Test
    fun readsVideos() {
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), YouTubeLinks.parse("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), YouTubeLinks.parse("Watch: https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), YouTubeLinks.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"))
        assertEquals(SharedLink.Video("dQw4w9WgXcQ"), YouTubeLinks.parse("https://m.youtube.com/live/dQw4w9WgXcQ"))
    }

    @Test
    fun readsPlaylists() {
        assertEquals(
            SharedLink.Playlist("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"),
            YouTubeLinks.parse("https://youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG&si=x"),
        )
    }

    @Test
    fun ignoresOtherLinks() {
        assertNull(YouTubeLinks.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLinks.parse("no link here"))
    }
}
