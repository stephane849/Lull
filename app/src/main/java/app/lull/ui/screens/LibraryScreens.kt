package app.lull.ui.screens

import app.lull.ui.components.Paper
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import app.lull.ui.continueIndex
import app.lull.ui.progressOf
import app.lull.ui.components.formatLeft
import app.lull.data.ResumePositions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.CollectionKind
import app.lull.core.InnerTube
import app.lull.core.MusicCollection
import app.lull.core.Page
import app.lull.core.Titles
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.Paged
import app.lull.ui.Screen
import app.lull.ui.components.Glyph
import app.lull.ui.components.Ink
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
import app.lull.ui.components.SquareIconButton
import app.lull.ui.components.TrackRow
import app.lull.ui.components.formatBytes
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
private fun miniPlayerBar(vm: AppViewModel): @Composable () -> Unit {
    val player by vm.player.state.collectAsStateWithLifecycle()
    return { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }) }
}

@Composable
fun LibraryScreen(vm: AppViewModel) {
    ScreenFrame(title = "Library", onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        val chevron: @Composable () -> Unit = { LineIcon(Glyph.CHEVRON, Modifier.padding(end = 12.dp)) }
        ListRow("Downloads", "${vm.downloadedTracks.size} songs · plays offline", { vm.go(Screen.Downloads) }, trailing = chevron, big = true)
        if (vm.signedIn) {
            ListRow("Playlists", "Your saved playlists", { vm.go(Screen.LibraryShelf("Playlists", InnerTube.LIBRARY_PLAYLISTS)) }, trailing = chevron, big = true)
            ListRow("Liked songs", "Songs you liked", { vm.go(Screen.Collection(vm.likedSongs())) }, trailing = chevron, big = true)
            ListRow("Podcasts", "Saved shows and episodes for later", { vm.go(Screen.LibraryShelf("Podcasts", InnerTube.LIBRARY_PODCASTS)) }, trailing = chevron, big = true)
            ListRow("Albums", "Saved albums", { vm.go(Screen.LibraryShelf("Albums", InnerTube.LIBRARY_ALBUMS)) }, trailing = chevron, big = true)
            ListRow("Artists", "Artists in your library", { vm.go(Screen.LibraryShelf("Artists", InnerTube.LIBRARY_ARTISTS)) }, trailing = chevron, big = true)
        } else {
            TextMMD("Sign in to see your playlists, liked songs, podcasts, albums and artists.", fontSize = 17.sp, color = Muted,
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
                subtitle = track.artist.takeUnless(Titles::isCount),
                onClick = { vm.player.play(tracks, paged.page * paged.pageSize + i, "Downloads") },
                onLongClick = { vm.go(Screen.TrackActions(track)) },
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
            ListRow(
                c.title, c.subtitle.replace(" • ", " · "), { vm.go(Screen.Collection(c)) },
                onLongClick = { vm.go(Screen.CollectionActions(c)) },
                trailing = {
                    TextMMD("⋯", fontSize = 26.sp, modifier = Modifier.size(48.dp)
                        .clickable(role = Role.Button) { vm.go(Screen.CollectionActions(c)) }
                        .semantics { contentDescription = "Options for ${c.title}" }
                        .padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                },
            )
        }
        if (paged.items.isNotEmpty()) Pager(paged)
    }
}

@Composable
fun CollectionScreen(vm: AppViewModel, collection: MusicCollection) {
    val paged = vm.collectionTracks(collection.browseId)
    LaunchedEffect(paged) { if (!paged.started) paged.start() }
    val tracks = paged.ordered
    val isPodcast = collection.kind == CollectionKind.PODCAST
    val canReverse = isPodcast || collection.kind == CollectionKind.PLAYLIST
    val noun = if (isPodcast) "episode" else "song"
    // Lecture series repeat the same title on every row; show only the part that differs.
    val shortTitles = remember(tracks) { Titles.distinct(tracks.map { it.title }) }
    // The owner's name is already under the title, so rows only repeat it when it differs.
    val owner = collection.subtitle.substringAfterLast("• ").trim()
    val resumeVersion by vm.resume.version.collectAsStateWithLifecycle()
    // Reversed order only makes sense once every episode is in.
    val settling = paged.reversed && !paged.complete
    val resumeAt = remember(tracks, resumeVersion, settling) {
        if (settling) null else continueIndex(vm.resume, tracks)
    }

    ScreenFrame(title = collection.kindLabel().ifEmpty { null } ?: collection.title, onBack = { vm.back() }, bottom = miniPlayerBar(vm)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)) {
            TextMMD(collection.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            val allDownloaded = tracks.isNotEmpty() && tracks.all { it.videoId in vm.downloaded }
            val count = when {
                tracks.isEmpty() -> null
                !paged.complete -> "${tracks.size}+ ${noun}s"
                tracks.size == 1 -> "1 $noun"
                else -> "${tracks.size} ${noun}s"
            }
            val meta = listOfNotNull(
                collection.subtitle.ifBlank { null },
                count,
                if (allDownloaded) "Downloaded" else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) TextMMD(meta, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            val library = vm.libraryStates[collection.browseId]
            val showOrder = canReverse && tracks.size > 1
            val canEdit = collection.browseId in vm.editable || collection.browseId == InnerTube.LIKED_SONGS
            val sub = vm.subscriptions[collection.browseId]
            if (library != null || showOrder || canEdit || sub != null) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (library != null) {
                        SavePill(library.saved) { vm.toggleSaved(collection) }
                    }
                    if (sub != null) {
                        SavePill(sub.subscribed, onLabel = "Subscribed", offLabel = "Subscribe") { vm.toggleSubscribed(collection.browseId) }
                    }
                    if (canEdit) {
                        Row(
                            Modifier.height(44.dp).border(2.dp, Ink, RoundedCornerShape(22.dp))
                                .clickable(role = Role.Button) { vm.go(Screen.EditPlaylist(collection)) }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { TextMMD("Edit", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    if (showOrder) {
                        val label = when {
                            isPodcast && paged.reversed -> "Oldest first"
                            isPodcast -> "Newest first"
                            paged.reversed -> "Reversed"
                            else -> "Playlist order"
                        }
                        Row(
                            Modifier.heightIn(min = 44.dp)
                                .clickable(role = Role.Button) { vm.setReversed(collection.browseId, !paged.reversed) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextMMD(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                            TextMMD(" ⇅", fontSize = 16.sp)
                        }
                    }
                }
            }
            vm.libraryError?.let { TextMMD(it, fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (!settling) {
            PlayButtons(
                vm, tracks, collection.title, showDownload = true,
                resumeAt = resumeAt,
                resumeLabel = resumeAt?.let { shortTitles.getOrElse(it) { tracks[it].title } },
                play = { index, shuffle -> vm.playFrom(collection, tracks, index, shuffle) },
            )
        }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)
        if (settling && paged.error == null) {
            TextMMD("Loading every ${noun} to list the oldest first…", fontSize = 17.sp, color = Muted,
                modifier = Modifier.padding(24.dp))
            return@ScreenFrame
        }
        PagedStatus(paged, empty = "No ${noun}s.")
        paged.pageItems.forEachIndexed { i, track ->
            val index = paged.page * paged.pageSize + i
            TrackRow(
                track = track,
                downloaded = track.videoId in vm.downloaded,
                downloading = track.videoId in vm.downloading,
                onPlay = { vm.playFrom(collection, tracks, index) },
                onQueue = { vm.go(Screen.TrackActions(track)) },
                onDownload = { vm.download(track) },
                number = index + 1,
                displayTitle = shortTitles.getOrElse(index) { track.title },
                showArtist = !track.artist.equals(owner, ignoreCase = true),
                progress = remember(track.videoId, resumeVersion) { progressOf(vm.resume, track) },
            )
        }
        if (tracks.isNotEmpty()) Pager(paged)
    }
}

/**
 * Play / Shuffle / Download all. With [resumeAt], a full-width "Continue" leads, since
 * picking up a series where you left it is the common case.
 */
@Composable
private fun PlayButtons(
    vm: AppViewModel,
    tracks: List<Track>,
    source: String,
    showDownload: Boolean,
    resumeAt: Int? = null,
    resumeLabel: String? = null,
    play: (index: Int, shuffle: Boolean) -> Unit = { index, shuffle ->
        if (shuffle) vm.player.shuffle(tracks, source) else vm.player.play(tracks, index, source)
    },
) {
    if (tracks.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resumeAt != null) {
            PrimaryButton("Continue: ${resumeLabel ?: "where you left off"}", Glyph.PLAY,
                { play(resumeAt, false) }, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (resumeAt != null) {
                SecondaryButton("From start", Glyph.PLAY, { play(0, false) }, Modifier.weight(1f))
            } else {
                PrimaryButton("Play", Glyph.PLAY, { play(0, false) }, Modifier.weight(1f))
            }
            SecondaryButton("Shuffle", Glyph.SHUFFLE, { play(0, true) }, Modifier.weight(1f))
            if (showDownload) {
                val allDone = tracks.all { it.videoId in vm.downloaded }
                SquareIconButton(
                    if (allDone) Glyph.DOWNLOADED else Glyph.DOWNLOAD,
                    if (allDone) "All downloaded" else "Download all",
                    { vm.downloadAll(tracks) },
                    enabled = !allDone,
                )
            }
        }
    }
}

/** "Save" / "In library": a pill, filled once saved, so the state reads at a glance on E Ink. */
@Composable
private fun SavePill(saved: Boolean, onLabel: String = "In library", offLabel: String = "Save", onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp)
            .background(if (saved) Ink else Paper, RoundedCornerShape(22.dp))
            .border(2.dp, Ink, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (saved) "$onLabel. Tap to undo." else offLabel }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(if (saved) Glyph.CHECK else Glyph.PLUS, size = 18.dp, color = if (saved) Paper else Ink)
        TextMMD(if (saved) onLabel else offLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (saved) Paper else Ink)
    }
}
