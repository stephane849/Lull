package app.lull.ui.screens

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
                    TextMMD(if (next != null) "Up next: ${next.title} · ${next.artist}" else "Nothing after this song",
                        fontSize = 16.sp, maxLines = 1)
                    TextMMD(
                        when (val n = state.upcoming.size) {
                            0 -> "Playback stops when this song ends"
                            1 -> "Queue ends after 1 more song"
                            else -> "Queue ends after $n more songs"
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

        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 32.dp)) {
            state.source?.let { TextMMD("From $it", fontSize = 16.sp, color = Muted) }
            TextMMD(state.title, fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp, maxLines = 3,
                modifier = Modifier.padding(top = 10.dp))
            TextMMD(state.artist, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
            // Updated every 15 s, or right away when tapped. No ticking progress bar.
            TextMMD(
                "${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}",
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 22.dp).clickable { vm.player.refresh() },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            BigControl(Glyph.PREVIOUS, "Previous", filled = false) { vm.player.previous() }
            BigControl(if (state.isPlaying) Glyph.PAUSE else Glyph.PLAY, if (state.isPlaying) "Pause" else "Play", filled = true) {
                vm.player.toggle()
            }
            BigControl(Glyph.NEXT, "Next", filled = false) { vm.player.next() }
        }

        val videoId = state.videoId
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isLiked = videoId != null && vm.liked[videoId] == true
            SmallControl(if (isLiked) Glyph.HEART_FILLED else Glyph.HEART, if (isLiked) "Liked" else "Like", Modifier.weight(1f)) {
                videoId?.let(vm::toggleLike)
            }
            val downloaded = videoId != null && videoId in vm.downloaded
            val downloading = videoId != null && videoId in vm.downloading
            SmallControl(
                when { downloaded -> Glyph.DOWNLOADED; downloading -> Glyph.PENDING; else -> Glyph.DOWNLOAD },
                when { downloaded -> "Downloaded"; downloading -> "Downloading"; else -> "Download" },
                Modifier.weight(1f),
            ) {
                if (videoId != null && !downloaded && !downloading) {
                    vm.download(app.lull.core.Track(videoId, state.title, state.artist, (state.durationMs / 1000).toInt().takeIf { it > 0 }))
                }
            }
            SmallControl(Glyph.MOON, if (vm.sleepMinutes == 0) "Sleep" else "${vm.sleepMinutes} min", Modifier.weight(1f)) {
                vm.cycleSleepTimer()
            }
            SmallControl(Glyph.FAST, formatSpeed(state.speed), Modifier.weight(1f)) { vm.player.cycleSpeed() }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0')) + "×"

@Composable
private fun BigControl(glyph: Glyph, label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(104.dp)
            .background(if (filled) Ink else Paper, RoundedCornerShape(16.dp))
            .border(2.dp, Ink, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(glyph, size = 42.dp, color = if (filled) Paper else Ink) }
}

@Composable
private fun SmallControl(glyph: Glyph, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(68.dp)
            .border(2.dp, Ink, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LineIcon(glyph, size = 22.dp)
        TextMMD(label, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun QueueScreen(vm: AppViewModel) {
    val state by vm.player.state.collectAsStateWithLifecycle()

    ScreenFrame(title = "Queue", onBack = { vm.back() }) {
        if (!state.hasMedia) {
            TextMMD("The queue is empty.", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(24.dp))
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
            "Up next · ${upcoming.size} ${if (upcoming.size == 1) "song" else "songs"}",
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
