package app.lull.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lull.core.Track
import app.lull.ui.Paged
import app.lull.ui.PlayerUi
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

val Ink = Color.Black
val Paper = Color.White
val Muted = Color(0xFF555555) // 7.5:1 on white

/** Every screen: optional header, content, optional bottom bar. Static, no transitions. */
@Composable
fun ScreenFrame(
    title: String?,
    onBack: (() -> Unit)?,
    scrollable: Boolean = true,
    action: (@Composable RowScope.() -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Paper).systemBarsPadding()) {
        if (title != null || onBack != null) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) IconTarget(Glyph.BACK, "Back", onBack, iconSize = 26)
                if (title != null) {
                    TextMMD(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                action?.invoke(this)
            }
            HorizontalDividerMMD(thickness = 2.dp, color = Ink)
        }
        // Pages are sized to fit; scrolling is only a fallback for small or large-font screens.
        val body = Modifier.weight(1f).fillMaxWidth()
        Column(if (scrollable) body.verticalScroll(rememberScrollState()) else body, content = content)
        bottom?.invoke()
    }
}

/** A 48dp tap target around a line icon. */
@Composable
fun IconTarget(glyph: Glyph, label: String, onClick: () -> Unit, iconSize: Int = 24, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LineIcon(glyph, size = iconSize.dp) }
}

@Composable
fun BigNavButton(label: String, sub: String, glyph: Glyph, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp)
            .border(2.dp, Ink, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LineIcon(glyph, size = 32.dp)
        Column {
            TextMMD(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            TextMMD(sub, fontSize = 15.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leading: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    big: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = if (big) 84.dp else 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) TextMMD(leading, fontSize = 16.sp, color = Muted, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            TextMMD(title, fontSize = if (big) 22.sp else 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                TextMMD(subtitle, fontSize = if (big) 15.sp else 14.sp, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
}

@Composable
fun TrackRow(
    track: Track,
    downloaded: Boolean,
    downloading: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    number: Int? = null,
) {
    ListRow(
        title = track.title,
        subtitle = listOfNotNull(track.artist.ifBlank { null }, track.durationSec?.let(::formatDuration)).joinToString(" · "),
        onClick = onPlay,
        onLongClick = onQueue,
        leading = number?.toString(),
        trailing = {
            when {
                downloaded -> Box(Modifier.size(48.dp).semantics { contentDescription = "Downloaded" }, Alignment.Center) { LineIcon(Glyph.DOWNLOADED) }
                downloading -> Box(Modifier.size(48.dp).semantics { contentDescription = "Downloading" }, Alignment.Center) { LineIcon(Glyph.PENDING) }
                else -> IconTarget(Glyph.DOWNLOAD, "Download ${track.title}", onDownload)
            }
        },
    )
}

/** Previous · Page 2 of 4 · Next. */
@Composable
fun <T> Pager(paged: Paged<T>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButtonMMD(onClick = paged::previous, enabled = paged.hasPrevious) { TextMMD("Previous", fontSize = 16.sp) }
        TextMMD(if (paged.loading) "Loading…" else paged.pageLabel, fontSize = 16.sp)
        OutlinedButtonMMD(onClick = paged::next, enabled = paged.hasNext && !paged.loading) {
            TextMMD("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Loading, error and empty states as plain text. */
@Composable
fun <T> PagedStatus(paged: Paged<T>, empty: String) {
    val error = paged.error
    val message = when {
        error != null -> error
        paged.loading && paged.items.isEmpty() -> "Loading…"
        paged.started && !paged.loading && paged.items.isEmpty() -> empty
        else -> null
    } ?: return
    TextMMD(message, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
}

@Composable
fun MiniPlayer(state: PlayerUi, onOpen: () -> Unit, onToggle: () -> Unit) {
    if (!state.hasMedia) return
    HorizontalDividerMMD(thickness = 2.dp, color = Ink)
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            TextMMD(state.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextMMD(state.artist, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(48.dp)
                .border(2.dp, Ink, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) { LineIcon(if (state.isPlaying) Glyph.PAUSE else Glyph.PLAY) }
    }
}

@Composable
fun PrimaryButton(label: String, glyph: Glyph?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ButtonMMD(onClick = onClick, modifier = modifier.height(56.dp)) {
        if (glyph != null) {
            LineIcon(glyph, size = 22.dp, color = Paper)
            Spacer(Modifier.width(10.dp))
        }
        TextMMD(label, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Paper)
    }
}

@Composable
fun SecondaryButton(label: String, glyph: Glyph?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButtonMMD(onClick = onClick, modifier = modifier.height(56.dp)) {
        if (glyph != null) {
            LineIcon(glyph, size = 22.dp)
            Spacer(Modifier.width(10.dp))
        }
        TextMMD(label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    else -> "0 MB"
}
