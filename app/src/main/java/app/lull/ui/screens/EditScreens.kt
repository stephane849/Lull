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

    val liked = vm.editingLiked
    val bar: @Composable () -> Unit = {
        ReorderBar(state, items.size, vm::moveItem, vm::removeItem, allowMove = !liked, removeLabel = if (liked) "Unlike" else "Remove")
    }
    ScreenFrame(
        title = if (liked) "Edit liked songs" else "Edit playlist",
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
                if (!liked) {
                    TextMMD("Rename", fontSize = 16.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
                        modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { name = vm.editTitle; renaming = true }.padding(12.dp))
                }
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

        if (!liked) {
            TextMMD(
                "Delete this playlist", fontSize = 16.sp, color = Muted, textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp).heightIn(min = 44.dp)
                    .clickable(role = Role.Button) { state.clear(); confirmDelete = true }.padding(12.dp),
            )
        }
    }
}

/**
 * Long-press (or ⋯) on a library item: open it, or take it out of your library. Loads the
 * item's page first to learn what's possible: delete (your playlists), remove from library
 * (saved playlists, albums, podcasts) or unsubscribe (artists).
 */
@Composable
fun CollectionActionsScreen(vm: AppViewModel, collection: MusicCollection) {
    val paged = vm.collectionTracks(collection.browseId)
    LaunchedEffect(paged) { if (!paged.started) paged.start() }
    var confirmDelete by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val library = vm.libraryStates[collection.browseId]
    val sub = vm.subscriptions[collection.browseId]
    val own = collection.browseId in vm.editable
    val checking = !paged.started || (paged.loading && paged.items.isEmpty())

    ScreenFrame(title = collection.kindLabel().ifEmpty { "Item" }, onBack = { vm.back() }) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
            TextMMD(collection.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (collection.subtitle.isNotBlank()) {
                TextMMD(collection.subtitle.replace(" • ", " · "), fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        (done ?: vm.libraryError)?.let { TextMMD(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)

        if (confirmDelete) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).border(2.5.dp, Ink, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextMMD("Delete “${collection.title}”?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextMMD("This removes it from YouTube Music everywhere. It can't be undone.", fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (vm.editBusy) "Deleting…" else "Delete", null,
                        { vm.deletePlaylist(collection.browseId) { ok -> if (!ok) { done = "Couldn't delete the playlist."; confirmDelete = false } } },
                        Modifier.weight(1f))
                    SecondaryButton("Keep it", null, { confirmDelete = false }, Modifier.weight(1f))
                }
            }
            return@ScreenFrame
        }

        ActionLine("Open") { vm.back(); vm.go(app.lull.ui.Screen.Collection(collection)) }
        when {
            checking -> TextMMD("Checking what you can change…", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            own -> {
                ActionLine("Edit") { vm.back(); vm.go(app.lull.ui.Screen.EditPlaylist(collection)) }
                ActionLine("Delete playlist") { confirmDelete = true }
            }
            library != null -> ActionLine(if (library.saved) "Remove from library" else "Save to library") {
                done = if (library.saved) "Removed from your library." else "Saved to your library."
                vm.toggleSaved(collection)
            }
            sub != null -> ActionLine(if (sub.subscribed) "Unsubscribe" else "Subscribe") {
                done = if (sub.subscribed) "Unsubscribed." else "Subscribed."
                vm.toggleSubscribed(collection.browseId)
            }
            else -> TextMMD("YouTube Music doesn't offer a way to remove this one.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun ActionLine(label: String, onClick: () -> Unit) {
    TextMMD(
        label, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    )
    HorizontalDividerMMD(thickness = 1.dp, color = Ink)
}
