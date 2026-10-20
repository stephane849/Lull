package app.lull.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lull.ui.AppViewModel
import app.lull.ui.Screen
import app.lull.ui.components.BigNavButton
import app.lull.ui.components.Glyph
import app.lull.ui.components.MiniPlayer
import app.lull.ui.components.Muted
import app.lull.ui.components.ScreenFrame
import com.mudita.mmd.components.text.TextMMD

/** Three doors and nothing else: no feed, no recommendations. */
@Composable
fun StartScreen(vm: AppViewModel) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    ScreenFrame(
        title = null,
        onBack = null,
        bottom = { MiniPlayer(player, { vm.go(Screen.NowPlaying) }, vm.player::toggle, { vm.player.seekBy(-15_000) }) },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 24.dp)) {
            TextMMD("Lull", fontSize = 52.sp, fontWeight = FontWeight.Bold)
            TextMMD("Play something on purpose.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!vm.signedIn) {
                BigNavButton("Sign in", "Connect your YouTube Music account", Glyph.NOTE) { vm.go(Screen.SignIn) }
            }
            BigNavButton("Search", "Songs, albums, artists", Glyph.SEARCH) { vm.go(Screen.Search) }
            BigNavButton("Library", "Downloads, playlists, liked songs", Glyph.LIBRARY) { vm.go(Screen.Library) }
            BigNavButton(
                "Now playing",
                if (player.hasMedia) "${player.title} · ${player.artist}" else "Nothing playing",
                Glyph.NOTE,
            ) { vm.go(Screen.NowPlaying) }
        }
        TextMMD(
            "Settings",
            fontSize = 18.sp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .heightIn(min = 44.dp)
                .clickable { vm.go(Screen.Settings) },
        )
    }
}
