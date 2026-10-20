package app.lull.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lull.core.Titles
import app.lull.core.Track
import app.lull.ui.AppViewModel
import app.lull.ui.Screen
import app.lull.ui.components.Glyph
import app.lull.ui.components.Ink
import app.lull.ui.components.LineIcon
import app.lull.ui.components.ListRow
import app.lull.ui.components.Muted
import app.lull.ui.components.PrimaryButton
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.formatDuration
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

/** What you can do with one song or episode. Opened by a long press on any row. */
@Composable
fun TrackActionsScreen(vm: AppViewModel, track: Track) {
    var done by remember(track) { mutableStateOf<String?>(null) }
    val long = (track.durationSec ?: 0) >= 600

    ScreenFrame(title = if (long) "Episode" else "Song", onBack = { vm.back() }) {
        TrackHeader(track)
        done?.let { TextMMD(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)) }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)

        ActionRow(Glyph.PLAY, "Play now") { vm.player.play(listOf(track), 0, null); vm.back() }
        ActionRow(Glyph.NEXT, "Play next") { vm.player.playNext(track); done = "Plays next." }
        ActionRow(Glyph.QUEUE, "Add to queue") { vm.player.addToQueue(track); done = "Added to the queue." }
        if (vm.signedIn) {
            ActionRow(Glyph.PLAYLIST_ADD, "Add to playlist") { vm.go(Screen.AddToPlaylist(track)) }
        }
        val downloaded = track.videoId in vm.downloaded
        val downloading = track.videoId in vm.downloading
        ActionRow(
            if (downloaded) Glyph.DOWNLOADED else Glyph.DOWNLOAD,
            when { downloaded -> "Remove download"; downloading -> "Downloading…"; else -> "Download" },
        ) {
            when {
                downloaded -> { vm.removeDownload(track.videoId); done = "Download removed." }
                !downloading -> { vm.download(track); done = "Downloading." }
            }
        }
        if (vm.signedIn) {
            val liked = vm.liked[track.videoId] == true
            ActionRow(if (liked) Glyph.HEART_FILLED else Glyph.HEART, if (liked) "Unlike" else "Like") {
                vm.toggleLike(track.videoId)
            }
        }
    }
}

/** Pick one of your playlists, or start a new one with this song in it. */
@Composable
fun AddToPlaylistScreen(vm: AppViewModel, track: Track) {
    LaunchedEffect(track.videoId) { vm.loadPlaylistTargets(track.videoId) }
    val focus = LocalFocusManager.current
    var name by remember { mutableStateOf("") }

    ScreenFrame(title = "Add to playlist", onBack = { vm.back() }) {
        TrackHeader(track)
        vm.playlistStatus?.let {
            TextMMD(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
        }
        HorizontalDividerMMD(thickness = 2.dp, color = Ink)

        TextMMD("New playlist", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextFieldMMD(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { TextMMD("Name", fontSize = 18.sp) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 19.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.createPlaylist(name, track) }),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton("Make", null, { focus.clearFocus(); vm.createPlaylist(name, track) }, Modifier.padding(start = 10.dp))
        }
        HorizontalDividerMMD(thickness = 1.dp, color = Ink)

        val targets = vm.playlistTargets
        when {
            targets == null -> TextMMD("Loading your playlists…", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            targets.isEmpty() -> TextMMD("No playlists yet. Name one above.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(24.dp))
            else -> targets.forEach { t ->
                ListRow(t.title, null, { vm.addToPlaylist(t, track) },
                    trailing = { LineIcon(Glyph.PLUS, Modifier.padding(end = 14.dp)) })
            }
        }
    }
}

@Composable
private fun TrackHeader(track: Track) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
        TextMMD(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(
            track.artist.takeUnless { it.isBlank() || Titles.isCount(it) },
            track.durationSec?.let(::formatDuration),
        ).joinToString(" · ")
        if (sub.isNotEmpty()) TextMMD(sub, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ActionRow(glyph: Glyph, label: String, onClick: () -> Unit) {
    ListRow(
        title = label,
        subtitle = null,
        onClick = onClick,
        trailing = { LineIcon(glyph, Modifier.padding(end = 14.dp)) },
    )
}

/** Shown for a moment while a shared YouTube link is looked up. */
@Composable
fun SharedScreen(vm: AppViewModel, screen: Screen.Shared) {
    LaunchedEffect(screen) { vm.resolveShared(screen) }
    ScreenFrame(title = "From YouTube", onBack = { vm.back() }) {
        TextMMD(vm.sharedError ?: "Opening…", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(24.dp))
    }
}
