package app.lull.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.core.MusicCollection
import app.lull.core.Titles
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.components.Ink
import app.lull.ui.components.Muted
import app.lull.ui.components.Paper
import app.lull.ui.components.PrimaryButton
import app.lull.ui.components.ReorderBar
import app.lull.ui.components.ReorderList
import app.lull.ui.components.ReorderState
import app.lull.ui.components.RowText
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.SecondaryButton
import app.lull.ui.components.formatDuration
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

internal fun Track.rowText(): RowText = RowText(
    title,
    listOfNotNull(artist.takeUnless { it.isBlank() || Titles.isCount(it) }, durationSec?.let(::formatDuration)).joinToString(" · "),
)

/** Reorder, remove, rename or delete one of your playlists. Every change saves right away. */
@Composable
fun EditPlaylistScreen(vm: AppViewModel, collection: MusicCollection) {
    LaunchedEffect(collection.browseId) { vm.startEditing(collection) }
    val paged = vm.collectionTracks(collection.browseId)
    LaunchedEffect(paged.complete, paged.items.size) { vm.syncEditItems(paged) }
    val state = remember { ReorderState() }
    val focus = LocalFocusManager.current
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(collection.title) }
    var confirmDelete by remember { mutableStateOf(false) }
    val items = vm.editItems

    val bar: @Composable () -> Unit = { ReorderBar(state, items.size, vm::moveItem, vm::removeItem) }
    ScreenFrame(
        title = "Edit playlist",
        onBack = null,
        action = {
            Box(
                Modifier.padding(end = 8.dp).height(44.dp).background(Ink, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button) { vm.finishEditing() }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { TextMMD("Done", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Paper) }
        },
        bottom = if (vm.canEditItems && !confirmDelete) bar else null,
    ) {
        if (renaming) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TextFieldMMD(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.renameEditing(name); renaming = false }),
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("Save", null, { focus.clearFocus(); vm.renameEditing(name); renaming = false }, Modifier.padding(start = 10.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextMMD(vm.editTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextMMD(
                        if (items.isEmpty()) "Loading…" else "${items.size} ${if (items.size == 1) "item" else "items"} · tap one to change it",
                        fontSize = 15.sp, color = Muted,
                    )
                }
                TextMMD("Rename", fontSize = 16.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { name = vm.editTitle; renaming = true }.padding(12.dp))
            }
        }
        vm.editError?.let { TextMMD(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)

        if (confirmDelete) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).border(2.5.dp, Ink, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextMMD("Delete “${vm.editTitle}”?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextMMD("This removes it from YouTube Music everywhere. It can't be undone.", fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (vm.editBusy) "Deleting…" else "Delete", null, { vm.deleteEditing() }, Modifier.weight(1f))
                    SecondaryButton("Keep it", null, { confirmDelete = false }, Modifier.weight(1f))
                }
            }
            return@ScreenFrame
        }

        when {
            paged.error != null && items.isEmpty() -> TextMMD(paged.error ?: "", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            items.isEmpty() && !paged.complete -> TextMMD("Loading every item…", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            items.isEmpty() -> TextMMD("This playlist is empty.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            !vm.canEditItems -> TextMMD("YouTube didn't send what's needed to edit this playlist here.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            else -> ReorderList(state, items.map { it.rowText() }, vm::moveItem)
        }

        TextMMD(
            "Delete this playlist", fontSize = 16.sp, color = Muted, textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp).heightIn(min = 44.dp)
                .clickable(role = Role.Button) { state.clear(); confirmDelete = true }.padding(12.dp),
        )
    }
}
