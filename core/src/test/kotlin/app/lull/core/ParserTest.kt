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
    fun readsPodcastEpisodes() {
        val json = """
        {"contents":[{"musicMultiRowListItemRenderer":{
          "title":{"runs":[{"text":"Lesson 12: Purification"}]},
          "subtitle":{"runs":[{"text":"Oct 5, 2026"},{"text":" • "},{"text":"1 hr 2 min"}]},
          "playNavigationEndpoint":{"watchEndpoint":{"videoId":"ep1"}}}}]}
        """.trimIndent()
        val tracks = Parser.tracks(Json.parseToJsonElement(json))
        assertEquals(listOf(Track("ep1", "Lesson 12: Purification", "Oct 5, 2026", 3720)), tracks)
    }

    @Test
    fun parsesSpokenDurations() {
        assertEquals(3720, Parser.parseSpokenDuration("1 hr 2 min"))
        assertEquals(3480, Parser.parseSpokenDuration("58 min"))
        assertNull(Parser.parseSpokenDuration("Oct 5, 2026"))
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

    @Test
    fun readsLibraryToggle() {
        val json = """
        {"header":{"musicResponsiveHeaderRenderer":{"buttons":[
          {"toggleButtonRenderer":{"isToggled":true,
            "defaultServiceEndpoint":{"likeEndpoint":{"status":"LIKE","target":{"playlistId":"OLAK5uy_abc"}}},
            "toggledServiceEndpoint":{"likeEndpoint":{"status":"INDIFFERENT","target":{"playlistId":"OLAK5uy_abc"}}}}}
        ]}}}
        """.trimIndent()
        assertEquals(LibraryState("OLAK5uy_abc", true), Parser.libraryState(Json.parseToJsonElement(json)))
        assertNull(Parser.libraryState(Json.parseToJsonElement("""{"header":{}}""")))
    }

    @Test
    fun readsPlaylistTargets() {
        val json = """
        {"contents":[{"addToPlaylistRenderer":{"playlists":[
          {"playlistAddToOptionRenderer":{"playlistId":"PL1","title":{"runs":[{"text":"Shama'il"}]}}},
          {"playlistAddToOptionRenderer":{"playlistId":"PL2","title":{"simpleText":"Drive"}}}
        ]}}]}
        """.trimIndent()
        assertEquals(
            listOf(PlaylistTarget("PL1", "Shama'il"), PlaylistTarget("PL2", "Drive")),
            Parser.playlistTargets(Json.parseToJsonElement(json)),
        )
    }

    @Test
    fun readsPlaylistEntryIdsAndOwnership() {
        val json = """
        {"header":{"musicEditablePlaylistDetailHeaderRenderer":{}},
         "contents":[{"musicResponsiveListItemRenderer":{
           "playlistItemData":{"videoId":"vid00000001","playlistSetVideoId":"SET1"},
           "flexColumns":[
             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Je te veux"}]}}},
             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Erik Satie"}]}}}
           ]}}]}
        """.trimIndent()
        val root = Json.parseToJsonElement(json)
        assertEquals("SET1", Parser.tracks(root).single().setVideoId)
        assertEquals(true, Parser.isEditable(root))
    }

    @Test
    fun readsSubscription() {
        val json = """{"header":{"musicImmersiveHeaderRenderer":{"subscriptionButton":{"subscribeButtonRenderer":{"channelId":"UCabc","subscribed":true}}}}}"""
        assertEquals(Subscription("UCabc", true), Parser.subscription(Json.parseToJsonElement(json)))
    }

    @Test
    fun readsSearchSections() {
        val json = """
        {"contents":[
          {"musicShelfRenderer":{"title":{"runs":[{"text":"Episodes"}]},"contents":[
            {"musicResponsiveListItemRenderer":{
              "playlistItemData":{"videoId":"ep000000001"},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Shama'il (Part 5)"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Episode"},{"text":" • "},{"text":"The Majlis"}]}}}
              ]}}]}},
          {"musicShelfRenderer":{"title":{"runs":[{"text":"Podcasts"}]},"contents":[
            {"musicResponsiveListItemRenderer":{
              "navigationEndpoint":{"browseEndpoint":{"browseId":"MPSPabc"}},
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"al-Shamail"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Podcast"},{"text":" • "},{"text":"The Majlis"}]}}}
              ]}}]}}
        ]}
        """.trimIndent()
        val sections = Parser.searchSections(Json.parseToJsonElement(json))
        assertEquals(listOf("Episodes", "Podcasts"), sections.map { it.title })
        assertEquals("The Majlis", sections[0].tracks.single().artist)
        assertEquals("MPSPabc", sections[1].collections.single().browseId)
    }

    @Test
    fun readsEpisodeLengthFromProgressBar() {
        val json = """
        {"contents":[{"musicMultiRowListItemRenderer":{
          "title":{"runs":[{"text":"Part 5"}]},
          "subtitle":{"runs":[{"text":"Oct 5, 2026"}]},
          "playbackProgress":{"musicPlaybackProgressRenderer":{"durationText":{"runs":[{"text":"1 hr 2 min"}]}}},
          "onTap":{"watchEndpoint":{"videoId":"ep000000005"}}}}]}
        """.trimIndent()
        val t = Parser.tracks(Json.parseToJsonElement(json)).single()
        assertEquals(3720, t.durationSec)
        assertEquals("Oct 5, 2026", t.artist)
    }
}
