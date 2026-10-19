package app.lull.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.InnerTube
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.Paged
import app.lull.ui.Screen
import app.lull.ui.components.Glyph
import app.lull.ui.components.IconTarget
import app.lull.ui.components.LineIcon
import app.lull.ui.components.ListRow
import app.lull.ui.components.MiniPlayer
import app.lull.ui.components.Muted
import app.lull.ui.components.PagedStatus
import app.lull.ui.components.Pager
import app.lull.ui.components.PrimaryButton
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.SecondaryButton
import app.lull.ui.components.TrackRow
import app.lull.ui.components.formatBytes
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mudita.mmd.components.text.TextMMD

@Composable
private fun miniPlayerBar(vm: AppViewModel): @Composable () -> Unit {
    val player by vm.player.state.collectAsStateWithLifecycle()
    return { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle) }
}

@Composable
fun LibraryScreen(vm: AppViewModel) {
    ScreenFrame(title = "Library", onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        val chevron: @Composable () -> Unit = { LineIcon(Glyph.CHEVRON, Modifier.padding(end = 12.dp)) }
        ListRow("Downloads", "${vm.downloadedTracks.size} songs · plays offline", { vm.go(Screen.Downloads) }, trailing = chevron, big = true)
        if (vm.signedIn) {
            ListRow("Playlists", "Your saved playlists", { vm.go(Screen.LibraryShelf("Playlists", InnerTube.LIBRARY_PLAYLISTS)) }, trailing = chevron, big = true)
            ListRow("Liked songs", "Songs you liked", { vm.go(Screen.Collection(vm.likedSongs())) }, trailing = chevron, big = true)
            ListRow("Albums", "Saved albums", { vm.go(Screen.LibraryShelf("Albums", InnerTube.LIBRARY_ALBUMS)) }, trailing = chevron, big = true)
            ListRow("Artists", "Artists in your library", { vm.go(Screen.LibraryShelf("Artists", InnerTube.LIBRARY_ARTISTS)) }, trailing = chevron, big = true)
        } else {
            TextMMD("Sign in to see your playlists, liked songs, albums and artists.", fontSize = 17.sp, color = Muted,
                modifier = Modifier.padding(24.dp))
        }
        TextMMD("${formatBytes(vm.bytesUsed)} downloaded", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val tracks = vm.downloadedTracks
    val paged = remember(tracks) { Paged(scope) { _ -> Page(tracks, null) } }
    LaunchedEffect(paged) { paged.start() }

    ScreenFrame(title = "Downloads", onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        PlayButtons(vm, tracks, "Downloads", showDownload = false)
        if (tracks.isEmpty()) {
            TextMMD("Nothing downloaded yet. Use the download button next to any song.", fontSize = 17.sp,
                color = Muted, modifier = Modifier.padding(24.dp))
        }
        paged.pageItems.forEachIndexed { i, track ->
            ListRow(
                title = track.title,
                subtitle = track.artist,
                onClick = { vm.player.play(tracks, paged.page * paged.pageSize + i, "Downloads") },
                onLongClick = { vm.player.addToQueue(track) },
                trailing = { IconTarget(Glyph.CLOSE, "Remove download of ${track.title}", { vm.removeDownload(track.videoId) }) },
            )
        }
        if (tracks.isNotEmpty()) Pager(paged)
    }
}

@Composable
fun LibraryShelfScreen(vm: AppViewModel, title: String, browseId: String) {
    val paged = vm.shelf(browseId)
    LaunchedEffect(paged) { if (!paged.started) paged.start() }

    ScreenFrame(title = title, onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        PagedStatus(paged, empty = "Nothing here yet.")
        paged.pageItems.forEach { c: MusicCollection ->
            ListRow(c.title, c.subtitle, { vm.go(Screen.Collection(c)) })
        }
        if (paged.items.isNotEmpty()) Pager(paged)
    }
}

@Composable
fun CollectionScreen(vm: AppViewModel, collection: MusicCollection) {
    val paged = vm.collectionTracks(collection.browseId)
    LaunchedEffect(paged) { if (!paged.started) paged.start() }
    val tracks = paged.items

    ScreenFrame(title = collection.kindLabel().ifEmpty { null } ?: collection.title, onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)) {
            TextMMD(collection.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            val allDownloaded = tracks.isNotEmpty() && tracks.all { it.videoId in vm.downloaded }
            val meta = listOfNotNull(
                collection.subtitle.ifBlank { null },
                if (tracks.isNotEmpty()) "${tracks.size} songs" else null,
                if (allDownloaded) "Downloaded" else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) TextMMD(meta, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        PlayButtons(vm, tracks, collection.title, showDownload = true)
        PagedStatus(paged, empty = "No songs.")
        paged.pageItems.forEachIndexed { i, track ->
            val index = paged.page * paged.pageSize + i
            TrackRow(
                track = track.copy(artist = track.artist.ifBlank { collection.subtitle.substringAfterLast("• ").trim() }),
                downloaded = track.videoId in vm.downloaded,
                downloading = track.videoId in vm.downloading,
                onPlay = { vm.player.play(tracks, index, collection.title) },
                onQueue = { vm.player.addToQueue(track) },
                onDownload = { vm.download(track) },
                number = index + 1,
            )
        }
        if (tracks.isNotEmpty()) Pager(paged)
    }
}

@Composable
private fun PlayButtons(vm: AppViewModel, tracks: List<Track>, source: String, showDownload: Boolean) {
    if (tracks.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryButton("Play", Glyph.PLAY, { vm.player.play(tracks, 0, source) }, Modifier.weight(1f))
        SecondaryButton("Shuffle", Glyph.SHUFFLE, { vm.player.shuffle(tracks, source) }, Modifier.weight(1f))
        if (showDownload) {
            val allDone = tracks.all { it.videoId in vm.downloaded }
            IconTarget(
                if (allDone) Glyph.DOWNLOADED else Glyph.DOWNLOAD,
                if (allDone) "All downloaded" else "Download all",
                { if (!allDone) vm.downloadAll(tracks) },
                iconSize = 26,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
