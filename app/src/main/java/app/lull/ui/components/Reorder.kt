package app.lull.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

data class RowText(val title: String, val subtitle: String?)

/**
 * Select-then-act reordering for E Ink (no dragging: it smears). Tap a row to select it,
 * then Up / Down / Move to… / Remove. "Move to…" shows "Put here" slots between rows.
 */
@Stable
class ReorderState(val pageSize: Int = 6) {
    var selected by mutableStateOf<Int?>(null)
    var moving by mutableStateOf(false)
    var page by mutableIntStateOf(0)

    fun clear() { selected = null; moving = false }

    /** Keeps the page showing [index] after a move. */
    fun follow(index: Int) { selected = index; page = index / pageSize }
}

/** The list part: rows, or rows with "Put here" slots while moving. */
@Composable
fun ReorderList(
    state: ReorderState,
    rows: List<RowText>,
    onMove: (from: Int, to: Int) -> Unit,
    numberOffset: Int = 0,
) {
    val n = rows.size
    val pages = maxOf(1, (n + state.pageSize - 1) / state.pageSize)
    val page = state.page.coerceIn(0, pages - 1)
    val from = state.selected

    if (state.moving && from != null && from < n) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                .background(Ink, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            TextMMD("MOVING #${from + 1 + numberOffset}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Paper, letterSpacing = 1.sp)
            TextMMD(rows[from].title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextMMD("Tap where it should go", fontSize = 15.sp, color = Paper)
        }
    }

    val start = page * state.pageSize
    val end = minOf(n, start + state.pageSize)
    Column(Modifier.fillMaxWidth()) {
        for (i in start until end) {
            if (state.moving && from != null) {
                // Slot i = "before row i". Slots next to the moving row change nothing.
                if (i != from && i != from + 1) PutHere(if (i == 0) "Put here · first" else "Put here") {
                    val to = if (i > from) i - 1 else i
                    onMove(from, to); state.moving = false; state.follow(to)
                }
                if (i == from) {
                    TextMMD("(${rows[i].title} is here now)", fontSize = 15.sp, color = Muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 56.dp, top = 6.dp, bottom = 6.dp))
                    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
                    continue
                }
            }
            EditRow(i + 1 + numberOffset, rows[i], selected = !state.moving && from == i) {
                if (!state.moving) state.selected = if (from == i) null else i
            }
        }
        if (state.moving && from != null && end == n && from != n - 1) {
            PutHere("Put here · last") {
                onMove(from, n - 1); state.moving = false; state.follow(n - 1)
            }
        }
    }

    if (pages > 1) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallOutlined("Previous", enabled = page > 0) { state.page = page - 1 }
            TextMMD("Page ${page + 1} of $pages", fontSize = 16.sp)
            SmallOutlined("Next", enabled = page < pages - 1) { state.page = page + 1 }
        }
    }
}

/** The bottom bar: acts on the selected row. */
@Composable
fun ReorderBar(
    state: ReorderState,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    HorizontalDividerMMD(thickness = 2.dp, color = Ink)
    val sel = state.selected
    if (sel == null || sel >= count) {
        TextMMD("Tap an item to move or remove it.", fontSize = 16.sp, color = Muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp))
        return
    }
    if (state.moving) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            BarButton("Cancel", "×", filled = false, enabled = true, modifier = Modifier.fillMaxWidth()) { state.moving = false }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BarButton("Up", "↑", filled = false, enabled = sel > 0, modifier = Modifier.weight(1f)) {
            onMove(sel, sel - 1); state.follow(sel - 1)
        }
        BarButton("Down", "↓", filled = false, enabled = sel < count - 1, modifier = Modifier.weight(1f)) {
            onMove(sel, sel + 1); state.follow(sel + 1)
        }
        BarButton("Move to…", "⇅", filled = true, enabled = count > 1, modifier = Modifier.weight(1f)) { state.moving = true }
        BarButton("Remove", "×", filled = false, enabled = true, modifier = Modifier.weight(1f)) {
            onRemove(sel); state.clear()
        }
    }
}

@Composable
private fun EditRow(number: Int, row: RowText, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Paper else Ink
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .background(if (selected) Ink else Paper)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(number.toString(), fontSize = 16.sp, color = if (selected) Paper else Muted, textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            TextMMD(row.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(row.subtitle?.ifBlank { null }, if (selected) "selected" else null).joinToString(" · ")
            if (sub.isNotEmpty()) TextMMD(sub, fontSize = 14.sp, color = if (selected) Paper else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
}

@Composable
private fun PutHere(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(44.dp)
            .border(2.dp, Ink, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TextMMD(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BarButton(label: String, symbol: String, filled: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = when { filled -> Paper; enabled -> Ink; else -> Muted }
    Column(
        modifier.height(60.dp)
            .background(if (filled) Ink else Paper, RoundedCornerShape(12.dp))
            .border(2.dp, if (enabled) Ink else Muted, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(symbol, fontSize = 20.sp, lineHeight = 20.sp, color = fg)
        TextMMD(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

@Composable
private fun SmallOutlined(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).border(2.dp, if (enabled) Ink else Muted, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { TextMMD(label, fontSize = 16.sp, color = if (enabled) Ink else Muted) }
}
