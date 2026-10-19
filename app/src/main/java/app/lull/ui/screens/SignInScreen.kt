package app.lull.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.lull.ui.AppViewModel
import app.lull.ui.components.Muted
import app.lull.ui.components.ScreenFrame
import com.mudita.mmd.components.text.TextMMD

private const val MUSIC = "https://music.youtube.com"
private const val LOGIN = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/**
 * Google's own sign-in page in a WebView. Lull never sees your password: once the page
 * lands back on music.youtube.com it keeps only the session cookies, encrypted on the phone.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(vm: AppViewModel) {
    ScreenFrame(title = "Sign in", onBack = { vm.back() }, scrollable = false) {
        TextMMD(
            "Sign in with the Google account you use for YouTube Music.",
            fontSize = 15.sp, color = Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from user agents marked as embedded WebViews.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url == null || !url.startsWith(MUSIC)) return
                            val cookie = CookieManager.getInstance().getCookie(MUSIC) ?: return
                            if ("SAPISID=" in cookie) {
                                CookieManager.getInstance().flush()
                                vm.onSignedIn(cookie)
                            }
                        }
                    }
                    loadUrl(LOGIN)
                }
            },
        )
    }
}
