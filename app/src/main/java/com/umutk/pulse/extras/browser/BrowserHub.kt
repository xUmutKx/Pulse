package com.umutk.pulse.extras.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageBg
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.io.ByteArrayInputStream

/**
 * The phone's own WebView, not a bundled Chromium: the whole feature (dark mode, ad blocking) is added on top of the
 * engine Android already ships, so it costs this APK next to nothing — a real Chromium-based browser (Chrome, Vanadium,
 * Firefox) is 150-250 MB because it carries its own engine, V8 and locale data in the APK; this one carries none of that.
 * The trade-off: no Chrome extension support — that API is not exposed to a plain WebView at any size.
 */
object BrowserPrefs {
    private fun p(c: Context) = c.getSharedPreferences("browser", Context.MODE_PRIVATE)
    fun home(c: Context): String = p(c).getString("home", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
    fun setHome(c: Context, v: String) = p(c).edit().putString("home", v).apply()
    fun darkMode(c: Context): Boolean = p(c).getBoolean("dark", true)
    fun setDarkMode(c: Context, v: Boolean) = p(c).edit().putBoolean("dark", v).apply()
    fun adBlock(c: Context): Boolean = p(c).getBoolean("adblock", true)
    fun setAdBlock(c: Context, v: Boolean) = p(c).edit().putBoolean("adblock", v).apply()
    fun bookmarks(c: Context): List<String> = p(c).getString("bookmarks", "").orEmpty().lines().filter { it.isNotBlank() }
    fun toggleBookmark(c: Context, url: String) {
        val l = bookmarks(c)
        p(c).edit().putString("bookmarks", (if (url in l) l - url else l + url).joinToString("\n")).apply()
    }
}

/**
 * A short, hand-picked list of common ad and tracker domains (a few dozen, not a real filter list with hundreds of
 * thousands of rules): good enough to quiet the worst of it without shipping or downloading a real block list.
 * A request is blocked when its host is this domain or a subdomain of it.
 */
private val AD_HOSTS = setOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com", "googletagmanager.com", "googletagservices.com",
    "adservice.google.com", "adnxs.com", "scorecardresearch.com", "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
    "pubmatic.com", "rubiconproject.com", "moatads.com", "2mdn.net", "adsrvr.org", "bidswitch.net", "casalemedia.com", "openx.net", "smartadserver.com",
    "media.net", "mopub.com", "applovin.com", "unityads.unity3d.com", "chartboost.com", "vungle.com", "ironsrc.com", "adcolony.com", "yandex.ru/ads",
    "connect.facebook.net", "ads.pinterest.com", "analytics.tiktok.com", "ads.linkedin.com", "bat.bing.com", "clarity.ms", "hotjar.com", "mixpanel.com",
    "segment.io", "segment.com", "appsflyer.com", "adjust.com", "branch.io", "flurry.com", "inmobi.com", "smaato.com", "yieldmo.com", "sharethrough.com",
)

private fun adHost(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false
    return AD_HOSTS.any { host == it || host.endsWith(".$it") }
}

@Composable
fun BrowserHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var addr by remember { mutableStateOf(BrowserPrefs.home(ctx)) }
    var pageUrl by remember { mutableStateOf(BrowserPrefs.home(ctx)) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(1f) }
    var loading by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(BrowserPrefs.darkMode(ctx)) }
    var adBlock by remember { mutableStateOf(BrowserPrefs.adBlock(ctx)) }
    var bookmarks by remember { mutableStateOf(BrowserPrefs.bookmarks(ctx)) }
    var showBookmarks by remember { mutableStateOf(false) }
    val webRef = remember { mutableStateOf<WebView?>(null) }
    val darkState = rememberUpdatedState(dark)
    val adBlockState = rememberUpdatedState(adBlock)

    @Suppress("DEPRECATION") // FORCE_DARK is the only option pre-Android 13's WebView; ALGORITHMIC_DARKENING above covers the newer ones
    fun applyDark(web: WebView) {
        val s = web.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, darkState.value)
        else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(s, if (darkState.value) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
    }

    fun go(target: String) {
        val t = target.trim()
        val url = when {
            t.isEmpty() -> return
            t.contains("://") -> t
            // a bare word or words with no dot: treat it as a search, not a broken address
            !t.contains('.') || t.contains(' ') -> "https://www.google.com/search?q=" + Uri.encode(t)
            else -> "https://$t"
        }
        addr = url
        webRef.value?.loadUrl(url)
    }

    BackHandler { if (canBack) webRef.value?.goBack() else onBack() }

    Column(Modifier.fillMaxSize().background(pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PIcon("back", T.text, 22.dp, Modifier.clickable { if (canBack) webRef.value?.goBack() else onBack() })
            if (canForward) Icon(Icons.Outlined.ArrowForward, null, Modifier.size(20.dp).clickable { webRef.value?.goForward() }, tint = T.text)
            PField(addr, { addr = it }, tr("Search or type a web address", "Ara veya web adresi yaz"), Modifier.weight(1f), onDone = { go(addr) })
            Icon(if (loading) Icons.Outlined.Close else Icons.Outlined.Refresh, null, Modifier.size(22.dp).clickable { if (loading) webRef.value?.stopLoading() else webRef.value?.reload() }, tint = T.text)
            Icon(if (pageUrl in bookmarks) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null,
                Modifier.size(22.dp).clickable { BrowserPrefs.toggleBookmark(ctx, pageUrl); bookmarks = BrowserPrefs.bookmarks(ctx) }, tint = if (pageUrl in bookmarks) com.umutk.pulse.Look.accent else T.text)
            Icon(Icons.Outlined.MoreVert, null, Modifier.size(22.dp).clickable { sheet = true }, tint = T.text)
        }
        if (loading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        Box(Modifier.weight(1f)) {
            AndroidView(Modifier.fillMaxSize(), factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    applyDark(this)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (adBlockState.value && adHost(request.url.host)) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            return super.shouldInterceptRequest(view, request)
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true; pageUrl = url ?: pageUrl; addr = url ?: addr
                            canBack = view.canGoBack(); canForward = view.canGoForward()
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false; title = view.title ?: ""; pageUrl = url ?: pageUrl
                            canBack = view.canGoBack(); canForward = view.canGoForward()
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                    }
                    webRef.value = this
                    loadUrl(pageUrl)
                }
            }, update = { web -> applyDark(web) })
        }
    }

    if (sheet) WinAlert(
        onDismissRequest = { sheet = false },
        title = { Text(title.ifBlank { tr("Browser", "Tarayıcı") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().clickable { go(BrowserPrefs.home(ctx)); sheet = false }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Home, null, Modifier.size(20.dp), T.sub); Spacer(Modifier.width(10.dp))
                    Text(tr("Home", "Ana sayfa"), color = T.text, fontSize = 15.sp)
                }
                Row(Modifier.fillMaxWidth().clickable { BrowserPrefs.setHome(ctx, pageUrl); sheet = false }, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Set current page as home", "Bu sayfayı ana sayfa yap"), color = T.text, fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth().clickable { showBookmarks = true; sheet = false }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bookmark, null, Modifier.size(20.dp), T.sub); Spacer(Modifier.width(10.dp))
                    Text(tr("Bookmarks", "Yer imleri") + " (${bookmarks.size})", color = T.text, fontSize = 15.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Dark mode", "Koyu mod"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PSwitch(dark, { dark = it; BrowserPrefs.setDarkMode(ctx, it); webRef.value?.let(::applyDark) })
                }
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Block ads and trackers", "Reklam ve izleyicileri engelle"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        PSwitch(adBlock, { adBlock = it; BrowserPrefs.setAdBlock(ctx, it) })
                    }
                    Text(tr("A short built-in list, not a full filter list", "Kısa, elle seçilmiş bir liste; tam bir filtre listesi değil"), color = T.sub, fontSize = 11.sp)
                }
                ExtraLookIcon(T.text)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ sheet = false }) { Text(tr("Close", "Kapat")) } },
    )

    if (showBookmarks) WinAlert(
        onDismissRequest = { showBookmarks = false },
        title = { Text(tr("Bookmarks", "Yer imleri")) },
        text = {
            if (bookmarks.isEmpty()) Text(tr("No bookmarks yet — tap the star next to a page", "Henüz yer imi yok — bir sayfanın yanındaki yıldıza dokun"), color = T.sub, fontSize = 13.sp)
            else LazyColumn(Modifier.height((bookmarks.size.coerceAtMost(6) * 48).dp)) {
                items(bookmarks) { b ->
                    Row(Modifier.fillMaxWidth().clickable { go(b); showBookmarks = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b, Modifier.weight(1f), color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.Close, null, Modifier.size(18.dp).clickable { BrowserPrefs.toggleBookmark(ctx, b); bookmarks = BrowserPrefs.bookmarks(ctx) }, tint = T.sub)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ showBookmarks = false }) { Text(tr("Close", "Kapat")) } },
    )

    DisposableEffect(Unit) { onDispose { webRef.value?.destroy() } }
}
