package com.umutk.pulse.extras.music

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/*
 * Web music: Spotify and YouTube Music as their own web players, inside Pulse. The player is the service's own web page:
 * it keeps its sign-in in the app's cookie store (not Pulse's data), needs no keys, and plays while the screen is in front.
 * Google may refuse to sign in inside a web view; then the player still works without an account.
 */
private class WebService(val name: String, val url: String, val home: String)

private val WEB_SERVICES = listOf(
    WebService("Spotify", "https://open.spotify.com/", "open.spotify.com"),
    WebService("YouTube Music", "https://music.youtube.com/", "music.youtube.com"),
)

// a desktop browser agent: the two sites serve their full player to it
private const val DESKTOP_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

@Composable
fun WebMusicHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var pick by rememberSaveable { mutableStateOf(0) }
    val webs = remember { HashMap<Int, WebView>() }
    BackHandler {
        val w = webs[pick]
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }
    Column(Modifier.fillMaxSize().background(T.panel).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text(tr("Web music", "Web müzik"), color = T.text, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            WEB_SERVICES.forEachIndexed { i, s ->
                val on = i == pick
                Text(s.name, color = if (on) T.accent else T.sub, fontSize = 14.sp,
                    modifier = Modifier.clickable { pick = i }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        // one web view per service, kept while the other is shown: a playing song is not stopped by a switch
        Box(Modifier.fillMaxSize()) {
            WEB_SERVICES.forEachIndexed { i, s ->
                val shown = i == pick
                Box(Modifier.fillMaxSize().then(if (shown) Modifier else Modifier.size(0.dp))) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c -> makeWeb(c, s).also { webs[i] = it } }, update = { })
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun makeWeb(c: android.content.Context, s: WebService): WebView {
    CookieManager.getInstance().setAcceptCookie(true)
    return WebView(c).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = DESKTOP_AGENT
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            // the player stays in its own service: other sites open in the phone's browser
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                return if (host.endsWith(s.home) || host.endsWith("google.com") || host.endsWith("spotify.com") || host.endsWith("youtube.com") || host.endsWith("googleusercontent.com") || host.endsWith("gstatic.com")) false
                else { runCatching { c.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }; true }
            }
        }
        webChromeClient = WebChromeClient()
        loadUrl(s.url)
    }
}
