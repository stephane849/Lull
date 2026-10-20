package app.lull.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.CollectionKind
import app.lull.core.MusicCollection
import app.lull.core.SearchFilter
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.Screen
import app.lull.ui.components.Ink
import app.lull.ui.components.ListRow
import app.lull.ui.components.MiniPlayer
import app.lull.ui.components.PagedStatus
import app.lull.ui.components.Pager
import app.lull.ui.components.Paper
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.TrackRow
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun SearchScreen(vm: AppViewModel) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val results = vm.searchResults

    ScreenFrame(
        title = "Search",
        onBack = { vm.back() },
        bottom = { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }) },
    ) {
        TextFieldMMD(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { TextMMD("Search YouTube Music", fontSize = 18.sp) },
            // Keep MMD's Lato; only enlarge it.
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 19.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        FilterTabs(vm.filter) { vm.setSearchFilter(it) }

        PagedStatus(results, empty = if (results.started) "No results." else "")
        results.pageItems.forEach { item ->
            when (item) {
                is Track -> TrackRow(
                    track = item,
                    downloaded = item.videoId in vm.downloaded,
                    downloading = item.videoId in vm.downloading,
                    onPlay = { vm.player.play(listOf(item), from = "Search") },
                    onQueue = { vm.go(Screen.TrackActions(item)) },
                    onDownload = { vm.download(item) },
                )
                is MusicCollection -> ListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = { vm.go(Screen.Collection(item)) },
                )
            }
        }
        if (results.items.isNotEmpty()) Pager(results)
    }
}

@Composable
private fun FilterTabs(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val labels = listOf(
        SearchFilter.SONGS to "Songs",
        SearchFilter.ALBUMS to "Albums",
        SearchFilter.ARTISTS to "Artists",
        SearchFilter.PLAYLISTS to "Playlists",
        SearchFilter.PODCASTS to "Podcasts",
        SearchFilter.EPISODES to "Episodes",
    )
    // Two rows of three so every label fits the Kompakt's narrow screen.
    labels.chunked(3).forEach { row ->
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            .border(2.dp, Ink, RoundedCornerShape(10.dp)),
    ) {
        row.forEach { (filter, label) ->
            val isSelected = filter == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .background(if (isSelected) Ink else Paper, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Tab) { onSelect(filter) }
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Paper else Ink,
                )
            }
        }
    }
    }
}

/** Search results can include artists; opening one shows that artist's songs. */
internal fun MusicCollection.kindLabel(): String = when (kind) {
    CollectionKind.PLAYLIST -> "Playlist"
    CollectionKind.ALBUM -> "Album"
    CollectionKind.ARTIST -> "Artist"
    CollectionKind.PODCAST -> "Podcast"
    CollectionKind.OTHER -> ""
}
