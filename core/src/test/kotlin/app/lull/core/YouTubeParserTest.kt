package app.lull.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeParserTest {

    private val likedJson = """
    {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"itemSectionRenderer":{"contents":[{"playlistVideoListRenderer":{"contents":[
        {"playlistVideoRenderer":{"videoId":"v1","title":{"runs":[{"text":"Family Night talk"}]},
          "shortBylineText":{"runs":[{"text":"NBIC"}]},"lengthSeconds":"3240","isPlayable":true}},
        {"playlistVideoRenderer":{"videoId":"v2","title":{"runs":[{"text":"[Private video]"}]},"isPlayable":false}},
        {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}
      ]}}]}}
    ]}}}}]}}}
    """.trimIndent()

    @Test
    fun readsLikedVideosAndSkipsUnplayable() {
        val root = Json.parseToJsonElement(likedJson)
        assertEquals(listOf(Track("v1", "Family Night talk", "NBIC", 3240)), Parser.youtubeTracks(root))
        assertEquals("NEXT", Parser.continuation(root))
    }

    @Test
    fun readsPlaylistsInBothLayouts() {
        val json = """
        {"items":[
          {"gridPlaylistRenderer":{"playlistId":"PLold","title":{"runs":[{"text":"Old layout"}]},"shortBylineText":{"runs":[{"text":"Someone"}]}}},
          {"lockupViewModel":{"contentId":"PLnew","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"New layout"}}}}},
          {"lockupViewModel":{"contentId":"RDmix","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"A mix"}}}}},
          {"lockupViewModel":{"contentId":"vid","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"A video"}}}}}
        ]}
        """.trimIndent()
        val lists = Parser.youtubePlaylists(Json.parseToJsonElement(json))
        assertEquals(listOf("VLPLold", "VLPLnew"), lists.map { it.browseId })
        assertEquals("YouTube playlist · Someone", lists[0].subtitle)
    }
}
