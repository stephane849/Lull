package app.lull.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParserTest {

    private val searchJson = """
    {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
      {"musicShelfRenderer":{"contents":[
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"abc123"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Gymnopédie No. 1"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Erik Satie","navigationEndpoint":{"browseEndpoint":{"browseId":"UCx",
                "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
              {"text":" • "},{"text":"Piano Works"},{"text":" • "},{"text":"3:05"}]}}}
          ]}},
        {"musicResponsiveListItemRenderer":{
          "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_1",
            "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Piano Works"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Album"},{"text":" • "},{"text":"Erik Satie"}]}}}
          ]}}
      ],"continuations":[{"nextContinuationData":{"continuation":"TOKEN1"}}]}}
    ]}}}}]}}}
    """.trimIndent()

    @Test
    fun readsTracksArtistAndDuration() {
        val tracks = Parser.tracks(Json.parseToJsonElement(searchJson))
        assertEquals(1, tracks.size)
        assertEquals(Track("abc123", "Gymnopédie No. 1", "Erik Satie", 185), tracks[0])
    }

    @Test
    fun readsCollections() {
        val collections = Parser.collections(Json.parseToJsonElement(searchJson))
        assertEquals(1, collections.size)
        assertEquals("MPREb_1", collections[0].browseId)
        assertEquals(CollectionKind.ALBUM, collections[0].kind)
        assertEquals("Album • Erik Satie", collections[0].subtitle)
    }

    @Test
    fun readsContinuation() {
        assertEquals("TOKEN1", Parser.continuation(Json.parseToJsonElement(searchJson)))
        assertNull(Parser.continuation(Json.parseToJsonElement("{}")))
    }

    @Test
    fun parsesDurations() {
        assertEquals(185, Parser.parseDuration("3:05"))
        assertEquals(3723, Parser.parseDuration("1:02:03"))
        assertNull(Parser.parseDuration("3 songs"))
    }

    @Test
    fun buildsSapisidHash() {
        val creds = Credentials("PREF=x; SAPISID=abc/def; HSID=y")
        assertEquals("abc/def", creds.sapisid)
        val header = creds.authorization("https://music.youtube.com", nowSec = 1000)!!
        assertEquals(true, header.startsWith("SAPISIDHASH 1000_"))
        assertEquals("SAPISIDHASH 1000_".length + 40, header.length)
    }
}
