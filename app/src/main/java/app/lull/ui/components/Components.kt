package app.lull.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lull.core.Titles
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
    titleLines: Int = 1,
    label: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = if (big) 84.dp else 68.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .padding(start = if (leading != null) 12.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            TextMMD(leading, fontSize = 16.sp, color = Muted, textAlign = TextAlign.End, maxLines = 1,
                modifier = Modifier.width(30.dp).padding(end = 2.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            TextMMD(title, fontSize = if (big) 22.sp else 18.sp, fontWeight = FontWeight.Bold,
                lineHeight = if (big) 26.sp else 22.sp, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                TextMMD(subtitle, fontSize = if (big) 15.sp else 14.sp, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.invoke()
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
}

/**
 * A song or episode. [displayTitle] lets a list show only the part of the title that
 * differs (see Titles.distinct); the full title is still what screen readers hear.
 */
@Composable
fun TrackRow(
    track: Track,
    downloaded: Boolean,
    downloading: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    number: Int? = null,
    displayTitle: String = track.title,
    showArtist: Boolean = true,
    progress: String? = null,
    label: String? = null,
) {
    val artist = track.artist.takeIf { showArtist && it.isNotBlank() && !Titles.isCount(it) }
    ListRow(
        title = displayTitle,
        subtitle = listOfNotNull(label, artist, track.durationSec?.let(::formatLength), progress).joinToString(" · "),
        onClick = onPlay,
        onLongClick = onQueue,
        leading = number?.toString(),
        titleLines = 2,
        label = if (displayTitle != track.title) track.title else null,
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

/** Long items (classes, episodes) get skip-back and time left; songs keep it simple. */
const val LONG_ITEM_MS = 10 * 60_000L

@Composable
fun MiniPlayer(state: PlayerUi, onOpen: () -> Unit, onToggle: () -> Unit, onRewind: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    if (!state.hasMedia) return
    val long = state.durationMs >= LONG_ITEM_MS
    HorizontalDividerMMD(thickness = 2.dp, color = Ink)
    Row(
        Modifier.fillMaxWidth().height(80.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
            // Middle ellipsis keeps the end of the title, where "(Part 3)" or "Ep. 12" usually is.
            TextMMD(state.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            val sub = when {
                state.error != null -> "${state.error} · tap play to retry"
                state.isLoading -> "Loading…"
                else -> listOfNotNull(
                    state.artist.takeUnless { it.isBlank() || Titles.isCount(it) },
                    if (long) formatLeft((state.durationMs - state.positionMs).coerceAtLeast(0)) else null,
                ).joinToString(" · ")
            }
            if (sub.isNotEmpty()) TextMMD(sub, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (long && onRewind != null) SkipButton(Glyph.REWIND, "15", "Back 15 seconds", onRewind, size = 52)
        PlayPauseButton(state.isPlaying, onToggle, size = 60, loading = state.isLoading)
        if (onClose != null) {
            Box(
                Modifier.size(44.dp)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "Stop and close player" },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Glyph.CLOSE, size = 20.dp) }
        }
    }
}

/** The round, filled play/pause: the one solid shape on the screen. */
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, size: Int, loading: Boolean = false) {
    // Loading shows three still dots (no spinner, for E Ink); tapping it stops the load.
    val glyph = when {
        loading -> Glyph.MORE
        isPlaying -> Glyph.PAUSE
        else -> Glyph.PLAY
    }
    Box(
        Modifier.size(size.dp)
            .background(Ink, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (loading) "Loading, tap to stop" else if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) { LineIcon(glyph, size = (size * 0.4f).dp, color = Paper) }
}

/** A round outlined button: arrow with seconds underneath, or just a track icon. */
@Composable
fun SkipButton(glyph: Glyph, seconds: String?, label: String, onClick: () -> Unit, size: Int = 84, enabled: Boolean = true) {
    val big = size >= 70
    Column(
        Modifier.size(size.dp)
            .border(2.5.dp, if (enabled) Ink else Muted, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (seconds == null) {
            LineIcon(glyph, size = if (big) 34.dp else 22.dp, color = if (enabled) Ink else Muted)
        } else {
            LineIcon(glyph, size = if (big) 26.dp else 18.dp)
            TextMMD(seconds, fontSize = if (big) 17.sp else 12.sp, fontWeight = FontWeight.Black,
                lineHeight = if (big) 18.sp else 13.sp)
        }
    }
}

private val ButtonPadding = PaddingValues(horizontal = 12.dp)

@Composable
fun PrimaryButton(label: String, glyph: Glyph?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ButtonMMD(onClick = onClick, modifier = modifier.height(56.dp), contentPadding = ButtonPadding) {
        ButtonContent(label, glyph, Paper)
    }
}

@Composable
fun SecondaryButton(label: String, glyph: Glyph?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButtonMMD(onClick = onClick, modifier = modifier.height(56.dp), contentPadding = ButtonPadding) {
        ButtonContent(label, glyph, Ink)
    }
}

/** Icon and label on one line: labels never wrap, they shrink to an ellipsis first. */
@Composable
private fun ButtonContent(label: String, glyph: Glyph?, color: Color) {
    if (glyph != null) {
        LineIcon(glyph, size = 20.dp, color = color)
        Spacer(Modifier.width(8.dp))
    }
    TextMMD(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/** A square outlined icon button, the same height, border and corners as [SecondaryButton]. */
@Composable
fun SquareIconButton(glyph: Glyph, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButtonMMD(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(0.dp),
    ) { LineIcon(glyph, size = 24.dp) }
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "36 min left", "1 hr 5 min left", "Under a minute left". */
fun formatLeft(ms: Long): String {
    val min = (ms / 60_000).toInt()
    return when {
        min < 1 -> "Under a minute left"
        min < 60 -> "$min min left"
        min % 60 == 0 -> "${min / 60} hr left"
        else -> "${min / 60} hr ${min % 60} min left"
    }
}

/** Songs as "3:05"; talks and episodes as "48 min" or "1 hr 12 min". */
fun formatLength(seconds: Int): String {
    if (seconds < 600) return formatDuration(seconds)
    val min = (seconds + 30) / 60
    return when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} hr"
        else -> "${min / 60} hr ${min % 60} min"
    }
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    else -> "0 MB"
}
