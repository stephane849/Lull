package app.lull.ui.screens

import app.lull.ui.components.formatLength
import app.lull.ui.lengthSec
import app.lull.core.CollectionKind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.InnerTube
import app.lull.core.Titles
import app.lull.data.Series
import app.lull.ui.AppViewModel
import app.lull.ui.PlayerUi
import app.lull.ui.Screen
import app.lull.ui.components.Glyph
import app.lull.ui.components.Ink
import app.lull.ui.components.MiniPlayer
import app.lull.ui.components.Muted
import app.lull.ui.components.PrimaryButton
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.SecondaryButton
import app.lull.ui.components.formatDuration
import app.lull.ui.continueIndex
import app.lull.ui.progressOf
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Home: "Pick up" for the series you're in the middle of, then a short index of places to
 * go. No feed, no recommendations. With nothing in progress, it's just the index.
 */
@Composable
fun StartScreen(vm: AppViewModel) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val resumeVersion by vm.resume.version.collectAsStateWithLifecycle()
    val series = vm.lastSeries
    val resumeAt = remember(series, resumeVersion) {
        series?.let { continueIndex(vm.resume, it.tracks, assumeLong = it.collection.kind == CollectionKind.PODCAST) }
    }
    // The card is the player while it holds what's playing; no separate mini player then.
    val cardIsPlaying = series != null && player.hasMedia &&
        series.tracks.any { it.videoId == player.videoId }
    val showCard = series != null && (resumeAt != null || cardIsPlaying)

    val miniBar: @Composable () -> Unit = {
        MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }, vm.player::close)
    }
    ScreenFrame(
        title = null,
        onBack = null,
        // Home always fits on one screen: the index shrinks to the room the card leaves.
        scrollable = false,
        bottom = if (cardIsPlaying) null else miniBar,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextMMD("LULL", fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            TextMMD(remember { dayPart() }, fontSize = 15.sp, color = Muted)
        }
        HorizontalDividerMMD(thickness = 3.dp, color = Ink, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp))

        if (showCard && series != null) {
            PickUpCard(vm, series, resumeAt, player, cardIsPlaying)
        }

        val big = !showCard
        val offline = vm.downloadedTracks.size
        val rows = buildList {
            if (!vm.signedIn) add(IndexEntry("Sign in", "Your music") { vm.go(Screen.SignIn) })
            if (vm.signedIn) add(IndexEntry("Podcasts") { vm.go(Screen.LibraryShelf("Podcasts", InnerTube.LIBRARY_PODCASTS)) })
            add(IndexEntry("Search") { vm.go(Screen.Search) })
            if (vm.signedIn) {
                add(IndexEntry("Playlists") { vm.go(Screen.LibraryShelf("Playlists", InnerTube.LIBRARY_PLAYLISTS)) })
                add(IndexEntry("Liked") { vm.go(Screen.Collection(vm.likedSongs())) })
            }
            add(IndexEntry("Offline", if (offline > 0) "$offline ${if (offline == 1) "item" else "items"}" else null) {
                vm.go(Screen.Downloads)
            })
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Rows keep their full size when there's room and share what's left when there isn't.
            val natural = if (big) 68.dp else 57.dp
            val room = (maxHeight - TopGap - LinksHeight) / rows.size
            val fits = room >= MinRowHeight
            val rowHeight = if (fits) min(natural, room) else MinRowHeight
            val labelSp = with(LocalDensity.current) { (rowHeight * 0.68f).toSp() }.value
                .coerceAtMost(if (big) 40f else 34f)
            // Only on very small screens or very large fonts does the index scroll.
            Column(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 28.dp, end = 28.dp, top = TopGap)) {
                    rows.forEachIndexed { i, row -> IndexRow("%02d".format(i + 1), row, rowHeight, labelSp) }
                }
                Row(Modifier.height(LinksHeight).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
                    if (vm.signedIn) {
                        SmallLink("Albums") { vm.go(Screen.LibraryShelf("Albums", InnerTube.LIBRARY_ALBUMS)) }
                        SmallLink("Artists") { vm.go(Screen.LibraryShelf("Artists", InnerTube.LIBRARY_ARTISTS)) }
                    }
                    SmallLink("Settings") { vm.go(Screen.Settings) }
                }
            }
        }
    }
}

private class IndexEntry(val label: String, val note: String? = null, val onClick: () -> Unit)

private val TopGap = 8.dp
private val LinksHeight = 62.dp
private val MinRowHeight = 44.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickUpCard(vm: AppViewModel, series: Series, resumeAt: Int?, player: PlayerUi, isCurrent: Boolean) {
    val tracks = series.tracks
    val shortTitles = remember(tracks) { Titles.distinct(tracks.map { it.title }) }
    // While it plays, describe what's playing; otherwise, what's next to pick up.
    val index = if (isCurrent) tracks.indexOfFirst { it.videoId == player.videoId } else resumeAt ?: 0
    val track = tracks[index]
    val resumeVersion by vm.resume.version.collectAsStateWithLifecycle()
    val left = if (isCurrent && player.durationMs > 0) {
        app.lull.ui.components.formatLeft((player.durationMs - player.positionMs).coerceAtLeast(0))
    } else {
        remember(track.videoId, resumeVersion) { progressOf(vm.resume, track) }
            ?: lengthSec(vm.resume, track)?.let(::formatLength)
    }
    val single = series.collection.browseId.isEmpty()
    var confirmClear by remember(series) { mutableStateOf(false) }
    // Continue was tapped and the player hasn't caught up yet: show the dots straight away,
    // then hand over to the player's own loading state once it holds the item.
    var starting by remember(series) { mutableStateOf(false) }
    LaunchedEffect(isCurrent, player.isPlaying, player.isLoading, player.error) {
        if (isCurrent && (player.isPlaying || player.isLoading || player.error != null)) starting = false
    }
    LaunchedEffect(starting) {
        if (starting) { delay(20_000); starting = false }
    }
    val loading = starting || (isCurrent && player.isLoading)
    val pickUp: () -> Unit = {
        starting = true
        if (single) vm.player.play(tracks, 0, series.collection.title)
        else vm.playFrom(series.collection, tracks, index)
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp)
            .border(2.5.dp, Ink, RoundedCornerShape(16.dp))
            // Tap the card for Now playing (picking up first if needed); hold it to clear it.
            .combinedClickable(
                onClick = {
                    if (!isCurrent) pickUp()
                    vm.go(Screen.NowPlaying)
                },
                onClickLabel = "Open Now playing",
                onLongClick = { confirmClear = true },
                onLongClickLabel = "Clear Pick up",
            )
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
    ) {
        TextMMD(if (isCurrent) "NOW PLAYING" else "PICK UP", fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, color = Muted)
        TextMMD(series.collection.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val label = shortTitles.getOrElse(index) { track.title }
        val line = if (single) {
            listOfNotNull(series.collection.subtitle.ifBlank { null }, left).joinToString(" · ")
        } else listOfNotNull(
            if (label.length <= 28) label else "${index + 1} of ${tracks.size}",
            if (label.length <= 28 && tracks.size > 1) "${index + 1} of ${tracks.size}" else null,
            left,
        ).joinToString(" · ")
        TextMMD(line, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))

        if (tracks.size in 2..24) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                tracks.forEachIndexed { i, t ->
                    val fill = when {
                        vm.resume.isFinished(t.videoId) -> 1f
                        i == index && isCurrent && player.durationMs > 0 -> player.positionMs.toFloat() / player.durationMs
                        else -> {
                            val dur = (lengthSec(vm.resume, t) ?: 0) * 1000f
                            if (dur > 0) vm.resume.get(t.videoId) / dur else 0f
                        }
                    }.coerceIn(0f, 1f)
                    Segment(fill, Modifier.weight(1f).height(8.dp), RoundedCornerShape(2.dp))
                }
            }
        }

        if (confirmClear) {
            TextMMD("Clear Pick up? It comes back when you start something new.", fontSize = 16.sp,
                modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Clear", null, { vm.clearPickUp() }, Modifier.weight(1f))
                SecondaryButton("Keep", null, { confirmClear = false }, Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) {
                    // Three still dots (no spinner, for E Ink); tapping stops the load.
                    PrimaryButton("Loading", Glyph.MORE, {
                        starting = false
                        vm.player.toggle()
                    }, Modifier.weight(1f))
                } else if (isCurrent) {
                    PrimaryButton(if (player.isPlaying) "Pause" else "Resume", if (player.isPlaying) Glyph.PAUSE else Glyph.PLAY,
                        vm.player::toggle, Modifier.weight(1f))
                } else {
                    PrimaryButton("Continue", Glyph.PLAY, pickUp, Modifier.weight(1f))
                }
                if (!single) SecondaryButton("All parts", null, { vm.go(Screen.Collection(series.collection)) })
            }
        }
    }
}

/** [height] includes the 1dp rule underneath. */
@Composable
private fun IndexRow(number: String, entry: IndexEntry, height: Dp, labelSp: Float) {
    Row(
        Modifier.fillMaxWidth().height(height - 1.dp)
            .clickable(role = Role.Button, onClick = entry.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(number, fontSize = 15.sp, color = Muted, modifier = Modifier.width(36.dp))
        TextMMD(entry.label, fontSize = labelSp.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
            lineHeight = (labelSp * 1.1f).sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (entry.note != null) TextMMD(entry.note, fontSize = 15.sp, color = Muted)
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
}

@Composable
private fun SmallLink(label: String, onClick: () -> Unit) {
    TextMMD(
        label, fontSize = 17.sp, color = Muted,
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
    )
}

/** "Tuesday evening". */
private fun dayPart(): String {
    val now = Calendar.getInstance()
    val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(now.time)
    val part = when (now.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }
    return "$day $part"
}
