package app.lull.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.CollectionKind
import app.lull.core.MusicCollection
import app.lull.core.SearchFilter
import app.lull.core.SearchSection
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.Paged
import app.lull.ui.Screen
import app.lull.ui.components.Ink
import app.lull.ui.components.ListRow
import app.lull.ui.components.MiniPlayer
import app.lull.ui.components.Muted
import app.lull.ui.components.PagedStatus
import app.lull.ui.components.Pager
import app.lull.ui.components.Paper
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.TrackRow
import app.lull.ui.progressOf
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/** How a YouTube Music section shows in Lull: its heading, row label and "More" filter. */
private data class SectionKind(val heading: String, val rowLabel: String?, val filter: SearchFilter)

private fun kindOf(title: String): SectionKind? {
    val t = title.lowercase()
    return when {
        t == "songs" -> SectionKind("Songs", "Song", SearchFilter.SONGS)
        t == "episodes" -> SectionKind("Episodes", "Episode", SearchFilter.EPISODES)
        t == "podcasts" -> SectionKind("Podcasts", null, SearchFilter.PODCASTS)
        t == "albums" -> SectionKind("Albums", null, SearchFilter.ALBUMS)
        t == "artists" -> SectionKind("Artists", null, SearchFilter.ARTISTS)
        "playlist" in t -> SectionKind("Playlists", null, SearchFilter.PLAYLISTS)
        else -> null // "Videos", "Profiles": covered by the YouTube side, or not listenable.
    }
}

private const val ROWS_PER_SECTION = 3

@Composable
fun SearchScreen(vm: AppViewModel) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val resumeVersion by vm.resume.version.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    ScreenFrame(
        title = "Search",
        onBack = { vm.back() },
        bottom = { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }) },
    ) {
        SourceSwitch(vm.searchYouTube) { vm.setSearchSource(it) }

        TextFieldMMD(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { TextMMD(if (vm.searchYouTube) "Search all of YouTube" else "Search YouTube Music", fontSize = 18.sp) },
            // Keep MMD's Lato; only enlarge it.
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 19.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
        if (vm.searchYouTube) {
            TextMMD("Heard as audio. No Shorts or live streams.", fontSize = 14.sp, color = Muted,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp))
        }

        val searched = vm.searchedQuery
        if (searched == null) {
            RecentSearches(vm.recentSearches) { focus.clearFocus(); vm.search(it) }
            return@ScreenFrame
        }

        if (vm.searchYouTube) {
            val results = vm.youTubeResults
            HorizontalDividerMMD(thickness = 2.dp, color = Ink, modifier = Modifier.padding(top = 12.dp))
            PagedStatus(results, empty = "Nothing found on YouTube.")
            results.pageItems.forEach { item -> ResultRow(vm, item, label = "YouTube", resumeVersion = resumeVersion) }
            if (results.items.isNotEmpty()) Pager(results)
        } else {
            when {
                vm.musicLoading -> Status("Searching…")
                vm.musicError != null -> Status(vm.musicError ?: "")
                else -> {
                    val shown = vm.musicSections.mapNotNull { s -> kindOf(s.title)?.let { s to it } }
                    if (shown.isEmpty()) Status("Nothing found. Try the YouTube side for more.")
                    shown.forEach { (section, kind) -> Section(vm, searched, section, kind, resumeVersion) }
                }
            }
        }
    }
}

/** "More ›" from a section: every result of that kind, paged. */
@Composable
fun SearchMoreScreen(vm: AppViewModel, screen: Screen.SearchMore) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val resumeVersion by vm.resume.version.collectAsStateWithLifecycle()
    val paged = remember(screen) { vm.moreResults(screen.query, screen.filter) }
    LaunchedEffect(paged) { if (!paged.started) paged.start() }
    val kind = kindOf(screen.title)

    ScreenFrame(
        title = "${screen.title}: “${screen.query}”",
        onBack = { vm.back() },
        bottom = { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }) },
    ) {
        PagedStatus(paged, empty = "Nothing more.")
        paged.pageItems.forEach { ResultRow(vm, it, label = kind?.rowLabel, resumeVersion = resumeVersion) }
        if (paged.items.isNotEmpty()) Pager(paged)
    }
}

@Composable
private fun Section(vm: AppViewModel, query: String, section: SearchSection, kind: SectionKind, resumeVersion: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(kind.heading.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Muted,
            modifier = Modifier.weight(1f))
        TextMMD(
            "More ›", fontSize = 15.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
            modifier = Modifier.heightIn(min = 44.dp)
                .clickable(role = Role.Button) { vm.go(Screen.SearchMore(query, kind.filter, kind.heading)) }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
    val items: List<Any> = section.tracks + section.collections
    items.take(ROWS_PER_SECTION).forEach { ResultRow(vm, it, label = kind.rowLabel, resumeVersion = resumeVersion) }
}

/** One result: a song, episode or YouTube recording, or a playlist, album, podcast or artist. */
@Composable
private fun ResultRow(vm: AppViewModel, item: Any, label: String?, resumeVersion: Int) {
    when (item) {
        is Track -> TrackRow(
            track = item,
            downloaded = item.videoId in vm.downloaded,
            downloading = item.videoId in vm.downloading,
            onPlay = { vm.player.play(listOf(item), from = "Search") },
            onQueue = { vm.go(Screen.TrackActions(item)) },
            onDownload = { vm.download(item) },
            label = label,
            progress = remember(item.videoId, resumeVersion) { progressOf(vm.resume, item) },
        )
        is MusicCollection -> ListRow(
            title = item.title,
            subtitle = item.subtitle.replace(" • ", " · ").ifBlank { kindName(item.kind) },
            onClick = { vm.go(Screen.Collection(item)) },
        )
    }
}

private fun kindName(kind: CollectionKind) = when (kind) {
    CollectionKind.PLAYLIST -> "Playlist"
    CollectionKind.ALBUM -> "Album"
    CollectionKind.ARTIST -> "Artist"
    CollectionKind.PODCAST -> "Podcast"
    CollectionKind.OTHER -> ""
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit) {
    if (recent.isEmpty()) {
        Status("Type what you're looking for, then press search.")
        return
    }
    TextMMD("RECENT", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Muted,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp))
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
    recent.forEach { q -> ListRow(q, null, { onPick(q) }) }
}

/** YouTube Music | YouTube. */
@Composable
private fun SourceSwitch(youTube: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).height(48.dp)
            .border(2.dp, Ink, RoundedCornerShape(24.dp)),
    ) {
        listOf(false to "YouTube Music", true to "YouTube").forEach { (value, label) ->
            val on = value == youTube
            Box(
                Modifier.weight(1f).height(48.dp)
                    .background(if (on) Ink else Paper, RoundedCornerShape(24.dp))
                    .clickable(role = Role.Tab) { onChange(value) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(label, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Paper else Ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Status(text: String) {
    TextMMD(text, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
}
