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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    // a small Tampermonkey-style extra: one script, run on every page. Not a userscript manager (no @match rules, no
    // per-site scripts, no script store) — just a single block of JavaScript the page runs after it loads.
    fun userScriptOn(c: Context): Boolean = p(c).getBoolean("userscript_on", false)
    fun setUserScriptOn(c: Context, v: Boolean) = p(c).edit().putBoolean("userscript_on", v).apply()
    fun userScript(c: Context): String = p(c).getString("userscript", "").orEmpty()
    fun setUserScript(c: Context, v: String) = p(c).edit().putString("userscript", v).apply()
}

/**
 * Common ad and tracker domains, not a real filter list with hundreds of thousands of rules (those run tens of MB):
 * 300+ distinct registrable domains, picked from how often each one appears across a well-known community hosts
 * list (anudeepND/blacklist), with the few that are also used for ordinary site hosting or an OEM's own account
 * services (amazonaws.com, cloudfront.net, microsoft.com, xiaomi.com, ...) deliberately left out so real sites and
 * logins do not break. A request is blocked when its host is this domain or a subdomain of it.
 */
private val AD_HOSTS = setOf(
    "1rx.io", "2mdn.net", "2o7.net", "33across.com", "360yield.com", "3lift.com", "7eer.net", "a-ads.com", "act-on.com", "actnx.com",
    "actonsoftware.com", "ad4game.com", "adacts.com", "adcolony.com", "addapptr.com", "addthis.com", "adform.net", "adgear.com", "adgrx.com",
    "adhigh.net", "adiquity.com", "adjust.com", "adk2x.com", "adkmob.com", "adlegend.com", "adlooxtracking.com", "admitad.com", "admob.com",
    "adnxs.com", "adomik.com", "adoric-om.com", "adotmob.com", "adreactor.com", "adriver.ru", "adroll.com", "ads.linkedin.com", "ads.pinterest.com",
    "adsafeprotected.com", "adsame.com", "adservice.google.com", "adskeeper.co.uk", "adsnative.com", "adsplay.in", "adsrvr.org", "adsupply.com",
    "adswizz.com", "adsymptotic.com", "adtdp.com", "adtech.de", "adtechus.com", "adtelligent.com", "advertising.com", "adx1.com", "adxxx.com",
    "aimtell.com", "akstat.io", "alphonso.tv", "amazon-adsystem.com", "amobee.com", "amplitude.com", "analytics.tiktok.com", "andbeyond.media",
    "appier.io", "appier.net", "applovin.com", "appnext.com", "appnexus.com", "appsflyer.com", "apptimize.com", "apxor.com", "aralego.com",
    "atdmt.com", "atlassolutions.com", "atomex.net", "atwola.com", "auditude.com", "automatad.com", "auxml.com", "bapb.gdn", "bat.bing.com",
    "batmobi.net", "beemray.com", "bfmio.com", "bidswitch.net", "blismedia.com", "bounceexchange.com", "branch.io", "brandreachsys.com",
    "brealtime.com", "brightroll.com", "btrll.com", "btttag.com", "buysellads.com", "carambo.la", "casalemedia.com", "chartbeat.net",
    "chartboost.com", "clarity.ms", "clickability.com", "clicktale.net", "clksite.com", "cnzz.com", "connatix.com", "connect.facebook.net",
    "connectad.io", "connexity.net", "content-ad.net", "contentabc.com", "contentdef.com", "contextweb.com", "coremetrics.com", "cpmstar.com",
    "crazyegg.com", "criteo.com", "criteo.net", "crittercism.com", "crwdcntrl.net", "ctnsnet.com", "datafirst.io", "demandbase.com", "demdex.net",
    "digitru.st", "districtm.io", "districtm.net", "domdex.com", "dotomi.com", "doubleclick.net", "doublepimp.com", "duapps.com", "dynamicyield.com",
    "ensighten.com", "ero-advertising.com", "everesttech.net", "evergage.com", "evyy.net", "exelator.com", "exoclick.com", "fitanalytics.com",
    "flurry.com", "free-counters.co.uk", "fwmrm.net", "fyber.com", "g2afse.com", "gameanalytics.com", "getclicky.com", "getui.com", "go-mpulse.net",
    "go2affise.com", "google-analytics.com", "googleadservices.com", "googlesyndication.com", "googletagmanager.com", "googletagservices.com",
    "hiido.com", "hotjar.com", "hoverr.media", "hyprmx.com", "igexin.com", "impactradius.com", "imrworldwide.com", "inclk.com", "inmobi.com",
    "inmobicdn.net", "inner-active.mobi", "inq.com", "insert.io", "insightexpressai.com", "intellitxt.com", "intentmedia.net", "ironbeast.io",
    "ironsrc.com", "juicyads.com", "justpremium.com", "kameleoon.com", "kameleoon.eu", "keywordblocks.com", "kissmetrics.com", "kochava.com",
    "ksapisrv.com", "ksmobile.com", "leanplum.com", "lemnisk.co", "lgsmartad.com", "liadm.com", "lijit.com", "linkury.com", "liveclicker.net",
    "liveperson.net", "localytics.com", "lpsnmedia.net", "m-pathy.com", "magnetic.com", "marfeel.com", "marinsm.com", "marketo.com", "mathtag.com",
    "measurementapi.com", "media.net", "mediaplex.com", "mediavine.com", "mgid.com", "mirtesen.ru", "mixpanel.com", "mktoresp.com", "mmstat.com",
    "moatads.com", "mobileapptracking.com", "mobvista.com", "moengage.com", "mookie1.com", "mopub.com", "mouseflow.com", "mpstat.us",
    "mtraction.com", "naiadsystems.com", "netcoresmartech.com", "news-subscribe.com", "offerstrack.net", "ojrq.net", "omtrdc.net",
    "online-metrix.net", "onthe.io", "openx.net", "optimizely.com", "optimonk.com", "optimove.net", "outbrain.com", "parsely.com", "perimeterx.net",
    "petametrics.com", "polarmobile.com", "presage.io", "propellerads.com", "pubmatic.com", "pubnative.net", "puserving.com", "pxf.io", "qgr.ph",
    "qualaroo.com", "quantserve.com", "redtrack.io", "reporo.net", "revcontent.com", "rtk.io", "rubiconproject.com", "scorecardresearch.com",
    "segment.com", "segment.io", "sharethrough.com", "singular.net", "skimresources.com", "smaato.com", "smaato.net", "smartadserver.com",
    "smartlook.com", "smi2.net", "smi2.ru", "smrtb.com", "snigelweb.com", "sokrati.com", "solocpm.com", "sonobi.com", "spotad.co",
    "startappservice.com", "stickyadstv.com", "supersonicads.com", "switchadhub.com", "taboola.com", "tapjoy.com", "teads.tv", "tealiumiq.com",
    "technoratimedia.com", "themoneytizer.com", "tlnk.io", "touchcommerce.com", "trafficjunky.net", "treasuredata.com", "tremorhub.com",
    "tribalfusion.com", "tubeadvertising.eu", "tubemogul.com", "tynt.com", "ucweb.com", "umeng.com", "unityads.unity3d.com", "unoadsrv.com",
    "urbanairship.com", "veinteractive.com", "vertamedia.com", "veruta.com", "videoplaza.tv", "vidible.tv", "viglink.com", "vizury.com",
    "voluumtrk.com", "voodoo-ads.io", "voodoo-tech.io", "vrtzads.com", "vungle.com", "w55c.net", "webengage.co", "webengage.com", "webtrekk.net",
    "wshareit.com", "xapads.com", "xpanama.net", "yieldmo.com", "yieldoptimizer.com", "youboranqs01.com", "zapr.in", "zedo.com", "zeotap.com",
    "zog.link",
)

private fun adHost(host: String?): Boolean {
    if (host.isNullOrEmpty()) return false
    return AD_HOSTS.any { host == it || host.endsWith(".$it") }
}

/** A page to open instead of the home page, for the one time another screen (Download manager's "open in Browser
 *  instead") starts this tool wanting a specific address rather than whatever the home page is set to. */
object BrowserNav { var pendingUrl: String? = null }

/** A forced-dark stylesheet for the stubborn bits WebView's own force-dark leaves alone (a header painted through an
 *  image or a gradient, a fixed top bar coloured by inline JS): common header/nav/top-bar class and id names, and the
 *  page root as a fallback, all painted pure black. Re-injected on every page load; an existing style tag is replaced
 *  so navigating within a page (or a reload) never stacks duplicate rules. */
private const val FORCE_DARK_CSS_JS = """(function(){
var css = 'html,body{background:#000!important}'+
'header,nav,[class*=header],[class*=Header],[class*=navbar],[class*=Navbar],[class*=topbar],[class*=top-bar],[class*=appbar],[id*=header],[id*=navbar]'+
'{background:#000!important;background-color:#000!important;background-image:none!important}';
var old = document.getElementById('__pulse_force_dark');
if (old) old.remove();
var s = document.createElement('style');
s.id = '__pulse_force_dark';
s.textContent = css;
document.documentElement.appendChild(s);
})();"""

@Composable
fun BrowserHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val startUrl = remember { BrowserNav.pendingUrl?.also { BrowserNav.pendingUrl = null } ?: BrowserPrefs.home(ctx) }
    var addr by remember { mutableStateOf(startUrl) }
    var pageUrl by remember { mutableStateOf(startUrl) }
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
    var userScriptOn by remember { mutableStateOf(BrowserPrefs.userScriptOn(ctx)) }
    var userScript by remember { mutableStateOf(BrowserPrefs.userScript(ctx)) }
    var showScript by remember { mutableStateOf(false) }
    val webRef = remember { mutableStateOf<WebView?>(null) }
    val darkState = rememberUpdatedState(dark)
    val adBlockState = rememberUpdatedState(adBlock)
    val userScriptState = rememberUpdatedState(userScriptOn to userScript)
    // a page's own HTML5 video fullscreen (the expand icon on an embedded player): WebView hands over a view of its own
    // for as long as the video stays fullscreen; without this callback pair that button silently does nothing at all
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var fullscreenCb by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    val activity = ctx as? android.app.Activity
    fun setImmersive(hide: Boolean) {
        val w = activity?.window ?: return
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (hide) { c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; c.hide(WindowInsetsCompat.Type.systemBars()) }
        else c.show(WindowInsetsCompat.Type.systemBars())
    }
    fun exitFullscreenVideo() {
        fullscreenCb?.onCustomViewHidden()
        fullscreenView = null; fullscreenCb = null
        setImmersive(false)
    }

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

    Box(Modifier.fillMaxSize()) {
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
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
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
                            // WebView's own dark mode (force-dark / algorithmic darkening) recolours ordinary text and
                            // backgrounds, but a header/top-bar painted through an image, a gradient or JS often survives
                            // it as-is and stays a plain white strip; force it black too, the same instinct as BlackOut's
                            // forced dark, just done in CSS since there is no app view tree to walk inside a WebView
                            if (darkState.value) view.evaluateJavascript(FORCE_DARK_CSS_JS, null)
                            val (on, script) = userScriptState.value
                            if (on && script.isNotBlank()) view.evaluateJavascript(script, null)
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                            fullscreenView = view; fullscreenCb = callback; setImmersive(true)
                        }
                        override fun onHideCustomView() { exitFullscreenVideo() }
                    }
                    webRef.value = this
                    loadUrl(pageUrl)
                }
            }, update = { web -> applyDark(web) })
        }
    }
    val fv = fullscreenView
    if (fv != null) {
        BackHandler { exitFullscreenVideo() }
        key(fv) { Box(Modifier.fillMaxSize().background(Color.Black)) { AndroidView(modifier = Modifier.fillMaxSize(), factory = { fv }) } }
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
                    Text(tr("A built-in list of 300+ domains, not a full filter list", "Yerleşik 300'den fazla alan adı listesi; tam bir filtre listesi değil"), color = T.sub, fontSize = 11.sp)
                }
                Column(Modifier.fillMaxWidth().clickable { showScript = true; sheet = false }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Run a script on every page", "Her sayfada bir betik çalıştır"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        PSwitch(userScriptOn, { userScriptOn = it; BrowserPrefs.setUserScriptOn(ctx, it) })
                    }
                    Text(tr("Edit the script", "Betiği düzenle"), color = T.sub, fontSize = 11.sp)
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

    if (showScript) {
        var draft by remember { mutableStateOf(userScript) }
        WinAlert(
            onDismissRequest = { showScript = false },
            title = { Text(tr("Script for every page", "Her sayfa için betik")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Plain JavaScript, run after each page loads. Not a userscript manager: one script, no @match rules, no script store.",
                        "Düz JavaScript, her sayfa yüklendikten sonra çalışır. Bir userscript yöneticisi değil: tek betik, @match kuralı yok, betik mağazası yok."),
                        color = T.sub, fontSize = 12.sp)
                    androidx.compose.material3.OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth().height(180.dp),
                        placeholder = { Text("document.body.style... ", color = T.sub) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = T.text),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = T.text, unfocusedTextColor = T.text, focusedBorderColor = com.umutk.pulse.Look.accent, unfocusedBorderColor = T.line, cursorColor = com.umutk.pulse.Look.accent),
                    )
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ userScript = draft; BrowserPrefs.setUserScript(ctx, draft); showScript = false }) { Text(tr("Save", "Kaydet")) } },
            dismissButton = { androidx.compose.material3.TextButton({ showScript = false }) { Text(tr("Cancel", "Vazgeç")) } },
        )
    }

    DisposableEffect(Unit) { onDispose { setImmersive(false); webRef.value?.destroy() } }
}
