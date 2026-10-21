package app.lull.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.lull.AppGraph
import app.lull.ui.AppViewModel
import app.lull.ui.Screen
import app.lull.ui.components.Ink
import app.lull.ui.components.Muted
import app.lull.ui.components.ScreenFrame
import app.lull.ui.components.formatBytes
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD

private const val SOURCE_URL = "https://github.com/stephane849/Lull"

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings = AppGraph.settings
    val context = LocalContext.current
    var wifiHigh by remember { mutableStateOf(settings.wifiHighQuality) }
    var mobileHigh by remember { mutableStateOf(settings.mobileHighQuality) }
    var downloadHigh by remember { mutableStateOf(settings.downloadHighQuality) }
    var wifiOnly by remember { mutableStateOf(settings.downloadWifiOnly) }
    var radio by remember { mutableStateOf(settings.radioAfterQueue) }

    ScreenFrame(title = "Settings", onBack = { vm.back() }) {
        Section("Account")
        SettingRow(if (vm.signedIn) "Signed in to YouTube Music" else "Not signed in") {
            OutlinedButtonMMD(onClick = { if (vm.signedIn) vm.signOut() else vm.go(Screen.SignIn) }) {
                TextMMD(if (vm.signedIn) "Sign out" else "Sign in", fontSize = 15.sp)
            }
        }

        Section("Streaming")
        QualityRow("Quality on Wi-Fi", wifiHigh) { wifiHigh = it; settings.wifiHighQuality = it }
        QualityRow("Quality on mobile", mobileHigh) { mobileHigh = it; settings.mobileHighQuality = it }

        Section("Downloads")
        QualityRow("Download quality", downloadHigh) { downloadHigh = it; settings.downloadHighQuality = it }
        SettingRow("Download on Wi-Fi only") {
            SwitchMMD(checked = wifiOnly, onCheckedChange = { wifiOnly = it; vm.setDownloadWifiOnly(it) })
        }
        SettingRow("Storage used") { TextMMD(formatBytes(vm.bytesUsed), fontSize = 17.sp) }

        Section("Playback")
        SettingRow("Radio after queue ends") {
            SwitchMMD(checked = radio, onCheckedChange = { radio = it; settings.radioAfterQueue = it })
        }

        Section("About")
        SettingRow("Lull · open source (GPL-3)") {
            TextMMD(
                "Source code",
                fontSize = 16.sp,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
                }.padding(8.dp),
            )
        }
        TextMMD(
            "Lull is an unofficial client and is not affiliated with YouTube or Google.",
            fontSize = 14.sp, color = Muted, modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun Section(title: String) {
    TextMMD(title.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        control()
    }
    HorizontalDividerMMD(thickness = 1.dp, color = Ink, modifier = Modifier.padding(horizontal = 24.dp))
}

/** Tapping flips between High and Normal. */
@Composable
private fun QualityRow(label: String, high: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label) {
        TextMMD(
            if (high) "High" else "Normal",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onChange(!high) }.padding(12.dp),
        )
    }
}
