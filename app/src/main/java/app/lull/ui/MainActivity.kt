package app.lull.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.lull.ui.screens.CollectionScreen
import app.lull.ui.screens.DownloadsScreen
import app.lull.ui.screens.LibraryScreen
import app.lull.ui.screens.LibraryShelfScreen
import app.lull.ui.screens.NowPlayingScreen
import app.lull.ui.screens.QueueScreen
import app.lull.ui.screens.SearchScreen
import app.lull.ui.screens.SettingsScreen
import app.lull.ui.screens.SignInScreen
import app.lull.ui.screens.StartScreen
import com.mudita.mmd.ThemeMMD

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            // MMD: monochrome colours, Lato typography, ripple off.
            ThemeMMD {
                // Pin MMD's Lato as the base style so every piece of text inherits it.
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    LullRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun LullRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    when (val screen = vm.screen) {
        Screen.Start -> StartScreen(vm)
        Screen.Search -> SearchScreen(vm)
        Screen.Library -> LibraryScreen(vm)
        Screen.Downloads -> DownloadsScreen(vm)
        is Screen.LibraryShelf -> LibraryShelfScreen(vm, screen.title, screen.browseId)
        is Screen.Collection -> CollectionScreen(vm, screen.collection)
        Screen.NowPlaying -> NowPlayingScreen(vm)
        Screen.Queue -> QueueScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.SignIn -> SignInScreen(vm)
    }
}
