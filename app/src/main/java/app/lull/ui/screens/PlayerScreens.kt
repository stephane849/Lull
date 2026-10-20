package app.lull.ui.screens

import androidx.compose.foundation.layout.RowScope
import app.lull.ui.components.RowText
import app.lull.ui.components.ReorderState
import app.lull.ui.components.ReorderList
import app.lull.ui.components.ReorderBar
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import app.lull.ui.components.LONG_ITEM_MS
import app.lull.ui.components.formatLeft
import app.lull.ui.components.SkipButton
import app.lull.ui.components.PlayPauseButton
import app.lull.ui.PlayerUi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.ui.AppViewModel
import app.lull.ui.Screen
import app.lull.ui.components.Glyph
import app.lull.ui.components.IconTarget
import app.lull.ui.components.Ink
import app.lull.ui.components.LineIcon
import app.lull.ui.components.ListRow
import app.lull.ui.components.Muted
import app.lull.ui.components.Paper
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.SecondaryButton
import app.lull.ui.components.formatMs
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun NowPlayingScreen(vm: AppViewModel) {
    val state by vm.player.state.collectAsStateWithLifecycle()

    ScreenFrame(
        title = "Now playing",
        onBack = { vm.back() },
        action = {
            TextMMD("Queue", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { vm.go(Screen.Queue) }.padding(14.dp))
        },
        bottom = {
            if (state.hasMedia) {
                HorizontalDividerMMD(thickness = 2.dp, color = Ink)
                Column(Modifier.fillMaxWidth().clickable { vm.go(Screen.Queue) }.padding(horizontal = 28.dp, vertical = 16.dp)) {
                    val next = state.upcoming.firstOrNull()
                    TextMMD(
                        if (next != null) "Up next: ${listOfNotNull(next.title, next.artist.ifBlank { null }).joinToString(" · ")}"
                        else "Nothing after this",
                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextMMD(
                        when (val n = state.upcoming.size) {
                            0 -> "Playback stops when this ends"
                            else -> "Queue ends after $n more"
                        },
                        fontSize = 15.sp, color = Muted,
                    )
                }
            }
        },
    ) {
        if (!state.hasMedia) {
            TextMMD("Nothing playing. Find something in Search or your Library.", fontSize = 18.sp, color = Muted,
                modifier = Modifier.padding(28.dp))
            return@ScreenFrame
        }

        val total = state.queue.size
        val long = state.durationMs >= LONG_ITEM_MS
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp)) {
            val where = listOfNotNull(state.source, if (total > 1) "${state.index + 1} of $total" else null).joinToString(" · ")
            if (where.isNotEmpty()) TextMMD(where, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextMMD(state.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (state.artist.isNotBlank()) TextMMD(state.artist, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }

        // Position updates every 15 s, or right away when tapped. No ticking.
        Column(Modifier.fillMaxWidth().clickable { vm.player.refresh() }.padding(start = 28.dp, end = 28.dp, top = 24.dp)) {
            ProgressStrip(state)
            Row(Modifier.padding(top = 8.dp)) {
                TextMMD("${formatMs(state.positionMs)} of ${formatMs(state.durationMs)}", fontSize = 17.sp)
                if (long) {
                    TextMMD(" · ", fontSize = 17.sp)
                    TextMMD(formatLeft((state.durationMs - state.positionMs).coerceAtLeast(0)), fontSize = 17.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        if (long) {
            // Talks: seconds matter more than tracks.
            Row(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(Glyph.REWIND, "15", "Back 15 seconds", { vm.player.seekBy(-15_000) })
                PlayPauseButton(state.isPlaying, vm.player::toggle, size = 128)
                SkipButton(Glyph.FORWARD, "30", "Forward 30 seconds", { vm.player.seekBy(30_000) })
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextLink("‹ Previous", enabled = true) { vm.player.previous() }
                TextLink("Next ›", enabled = state.upcoming.isNotEmpty()) { vm.player.next() }
            }
        } else {
            // Music: same shapes, but the round buttons change tracks.
            Row(
                Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkipButton(Glyph.PREVIOUS, null, "Previous", { vm.player.previous() })
                PlayPauseButton(state.isPlaying, vm.player::toggle, size = 128)
                SkipButton(Glyph.NEXT, null, "Next", { vm.player.next() }, enabled = state.upcoming.isNotEmpty())
            }
        }

        val videoId = state.videoId
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionButton(formatSpeed(state.speed), "Playback speed", Modifier.weight(1f)) { vm.player.cycleSpeed() }
            OptionButton(if (vm.sleepMinutes == 0) "Sleep" else "${vm.sleepMinutes} min", "Sleep timer", Modifier.weight(1f)) {
                vm.cycleSleepTimer()
            }
            val downloaded = videoId != null && videoId in vm.downloaded
            val downloading = videoId != null && videoId in vm.downloading
            OptionButton(
                when { downloaded -> "On device"; downloading -> "Saving…"; else -> "Download" },
                "Download",
                Modifier.weight(1f),
            ) {
                if (videoId != null && !downloaded && !downloading) {
                    vm.download(app.lull.core.Track(videoId, state.title, state.artist, (state.durationMs / 1000).toInt().takeIf { it > 0 }))
                }
            }
            val isLiked = videoId != null && vm.liked[videoId] == true
            OptionButton(if (isLiked) "Liked" else "Like", "Like", Modifier.weight(1f), bold = isLiked) {
                videoId?.let(vm::toggleLike)
            }
        }
        if (videoId != null) {
            val current = app.lull.core.Track(videoId, state.title, state.artist, (state.durationMs / 1000).toInt().takeIf { it > 0 })
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                if (vm.signedIn) TextLink("+ Add to playlist", enabled = true) { vm.go(Screen.AddToPlaylist(current)) }
                TextLink("More…", enabled = true) { vm.go(Screen.TrackActions(current)) }
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0')) + "×"

/**
 * Where you are. In a series (2 to 24 items) each segment is one part: filled when done,
 * partly filled for the current one. Otherwise one bar for the current item.
 */
@Composable
private fun ProgressStrip(state: PlayerUi) {
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val total = state.queue.size
    if (total in 2..24) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                val fill = when {
                    i < state.index -> 1f
                    i == state.index -> fraction
                    else -> 0f
                }
                Segment(fill, Modifier.weight(1f).height(10.dp), RoundedCornerShape(2.dp))
            }
        }
    } else {
        Segment(fraction, Modifier.fillMaxWidth().height(10.dp), RoundedCornerShape(5.dp))
    }
}

@Composable
internal fun Segment(fill: Float, modifier: Modifier, shape: Shape) {
    Box(modifier.clip(shape).border(2.dp, Ink, shape)) {
        if (fill > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Ink))
    }
}

@Composable
private fun TextLink(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextMMD(
        label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (enabled) Ink else Muted,
        modifier = Modifier.heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
    )
}

@Composable
private fun OptionButton(text: String, label: String, modifier: Modifier, bold: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp)
            .border(2.dp, Ink, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label: $text" },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text, fontSize = 15.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun QueueScreen(vm: AppViewModel) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val reorder = remember { ReorderState() }
    // Upcoming entry u is queue item index + 1 + u.
    val base = state.index + 1
    val move: (Int, Int) -> Unit = { f, t -> vm.player.moveItem(base + f, base + t) }
    val remove: (Int) -> Unit = { u -> vm.player.removeAt(base + u) }
    val bar: @Composable () -> Unit = { ReorderBar(reorder, state.upcoming.size, move, remove) }
    val editAction: @Composable RowScope.() -> Unit = {
        TextMMD(if (editing) "Done" else "Edit", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(role = Role.Button) { editing = !editing; reorder.clear() }.padding(14.dp))
    }

    ScreenFrame(
        title = "Queue",
        onBack = { vm.back() },
        action = if (state.upcoming.isNotEmpty()) editAction else null,
        bottom = if (editing && state.upcoming.isNotEmpty()) bar else null,
    ) {
        if (!state.hasMedia) {
            TextMMD("The queue is empty.", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(24.dp))
            return@ScreenFrame
        }
        if (editing) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                TextMMD("Playing now", fontSize = 15.sp, color = Muted)
                TextMMD(state.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextMMD("Up next · tap one to change it", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
            HorizontalDividerMMD(thickness = 2.dp, color = Ink)
            ReorderList(reorder, state.upcoming.map { RowText(it.title, it.artist) }, move)
            return@ScreenFrame
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            TextMMD("Playing now", fontSize = 15.sp, color = Muted)
            TextMMD(state.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            TextMMD(state.artist, fontSize = 16.sp)
        }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)

        val upcoming = state.upcoming
        TextMMD(
            "Up next · ${upcoming.size}",
            fontSize = 15.sp, color = Muted, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
        )
        upcoming.take(MAX_SHOWN).forEachIndexed { i, entry ->
            val queueIndex = state.index + 1 + i
            ListRow(
                title = entry.title,
                subtitle = entry.artist,
                onClick = {},
                trailing = { IconTarget(Glyph.CLOSE, "Remove ${entry.title}", { vm.player.removeAt(queueIndex) }) },
            )
        }
        if (upcoming.size > MAX_SHOWN) {
            TextMMD("and ${upcoming.size - MAX_SHOWN} more", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
        }

        // The calm ending: the queue stops, and says so.
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp)
                .border(2.dp, Ink, RoundedCornerShape(12.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextMMD("End of queue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextMMD("Playback stops here.", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton("Clear queue", null, { vm.player.clearUpcoming() }, Modifier.weight(1f))
            SecondaryButton("Start radio", null, { vm.player.startRadio() }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private const val MAX_SHOWN = 6
