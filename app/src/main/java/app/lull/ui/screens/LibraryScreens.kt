package app.lull.ui.screens

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

/** Progress shown on a row: only for long items (classes, episodes), never for songs. */
private fun progressOf(resume: ResumePositions, track: Track): String? {
    val dur = track.durationSec ?: return null
    if (dur < LONG_ITEM_SEC) return null
    if (resume.isFinished(track.videoId)) return "Played"
    val pos = resume.get(track.videoId)
    return if (pos > 0) formatLeft(dur * 1000L - pos) else null
}

/**
 * Where to pick up: the item played most recently, or the one after it if that was
 * finished. Null when nothing in the list has been started.
 */
private fun continueIndex(resume: ResumePositions, tracks: List<Track>): Int? {
    val last = tracks.indices.maxByOrNull { resume.lastPlayed(tracks[it].videoId) } ?: return null
    if (resume.lastPlayed(tracks[last].videoId) == 0L) return null
    if (!resume.isFinished(tracks[last].videoId)) return last
    return (last + 1 until tracks.size).firstOrNull { !resume.isFinished(tracks[it].videoId) }
}

private const val LONG_ITEM_SEC = 600

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
            if (canReverse && tracks.size > 1) {
                val label = when {
                    isPodcast && paged.reversed -> "Oldest first"
                    isPodcast -> "Newest first"
                    paged.reversed -> "Reversed"
                    else -> "Playlist order"
                }
                Row(
                    Modifier.padding(top = 4.dp).heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { vm.setReversed(collection.browseId, !paged.reversed) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextMMD("Order: ", fontSize = 16.sp, color = Muted)
                    TextMMD(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                }
            }
        }
        if (!settling) {
            PlayButtons(
                vm, tracks, collection.title, showDownload = true,
                resumeAt = resumeAt,
                resumeLabel = resumeAt?.let { shortTitles.getOrElse(it) { tracks[it].title } },
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
                onPlay = { vm.player.play(tracks, index, collection.title) },
                onQueue = { vm.player.addToQueue(track) },
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
) {
    if (tracks.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resumeAt != null) {
            PrimaryButton("Continue: ${resumeLabel ?: "where you left off"}", Glyph.PLAY,
                { vm.player.play(tracks, resumeAt, source) }, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (resumeAt != null) {
                SecondaryButton("From start", Glyph.PLAY, { vm.player.play(tracks, 0, source) }, Modifier.weight(1f))
            } else {
                PrimaryButton("Play", Glyph.PLAY, { vm.player.play(tracks, 0, source) }, Modifier.weight(1f))
            }
            SecondaryButton("Shuffle", Glyph.SHUFFLE, { vm.player.shuffle(tracks, source) }, Modifier.weight(1f))
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
