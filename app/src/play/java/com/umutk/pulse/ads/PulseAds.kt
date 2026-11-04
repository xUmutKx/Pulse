package com.umutk.pulse.ads

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * The ads of the play build: a banner, a native ad card in the app's own look, and a rewarded ad that unlocks a thing when it is watched to the end.
 * [USE_TEST] shows Google's test ads (safe to tap while developing); set it to false for the live ids before release.
 */
object PulseAds {
    const val ENABLED = true

    /** No ad is drawn while the premium is owned or the ad credit is open (the credit: a watched ad, 5 minutes). */
    fun off(c: Context): Boolean = Premium.on(c) || AdUnlocks.isOpen(c, AdUnlocks.PASS)
    const val USE_TEST = true
    private const val BANNER_LIVE = "ca-app-pub-2608227008506779/9168342683"
    private const val NATIVE_LIVE = "ca-app-pub-2608227008506779/8545526151"
    private const val REWARDED_LIVE = "ca-app-pub-2608227008506779/8920930672"
    private const val BANNER_TEST = "ca-app-pub-3940256099942544/6300978111"
    private const val NATIVE_TEST = "ca-app-pub-3940256099942544/2247696110"
    private const val REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"
    private val BANNER get() = if (USE_TEST) BANNER_TEST else BANNER_LIVE
    private val NATIVE get() = if (USE_TEST) NATIVE_TEST else NATIVE_LIVE
    private val REWARDED get() = if (USE_TEST) REWARDED_TEST else REWARDED_LIVE
    private var rewarded: RewardedAd? = null

    fun init(c: Context) {
        MobileAds.initialize(c) { }
        loadRewarded(c)
    }

    /** Premium from Google Play: asks Play at start (a reinstall gets it back). */
    fun restorePremium(c: Context, done: (Boolean) -> Unit = { }) = PremiumBilling.restore(c, done)

    /** Opens the Premium purchase inside the app. */
    fun buyPremium(a: Activity, done: (Boolean) -> Unit) = PremiumBilling.buy(a, done)

    /** A standard banner; it is released when it leaves the screen. */
    @Composable
    fun Banner(modifier: Modifier = Modifier) {
        // premium: no ads at all
        if (off(LocalContext.current)) return
        AndroidView(
            modifier = modifier,
            // a failed ad must never take the screen down: the place stays empty instead
            factory = { ctx -> runCatching { AdView(ctx).apply { setAdSize(AdSize.BANNER); adUnitId = BANNER; setBackgroundColor(android.graphics.Color.TRANSPARENT); loadAd(AdRequest.Builder().build()) } }.getOrElse { View(ctx) } },
            onRelease = { (it as? AdView)?.destroy() },
        )
    }

    /**
     * A native ad in the app's own look: the picture, headline, text and button of the ad, drawn with the colours given, and the word "Ad" as the label.
     * Nothing is shown until an ad is loaded.
     */
    @Composable
    fun NativeCard(modifier: Modifier = Modifier, bg: Color, text: Color, sub: Color, accent: Color) {
        val ctx = LocalContext.current
        if (off(ctx)) return
        var ad by remember { mutableStateOf<NativeAd?>(null) }
        DisposableEffect(Unit) {
            val loader = AdLoader.Builder(ctx, NATIVE).forNativeAd { ad = it }.build()
            loader.loadAd(AdRequest.Builder().build())
            onDispose { ad?.destroy(); ad = null }
        }
        val current = ad ?: return
        AndroidView(
            modifier = modifier,
            factory = { c -> nativeView(c, bg.toArgb(), text.toArgb(), sub.toArgb(), accent.toArgb()) },
            update = { view -> bindNative(view, current) },
        )
    }

    private fun nativeView(c: Context, bg: Int, text: Int, sub: Int, accent: Int): NativeAdView {
        fun dp(v: Int) = (v * c.resources.displayMetrics.density).toInt()
        val root = NativeAdView(c)
        // the card is see-through: the page shows behind it, and only the button keeps a black ground
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        val icon = ImageView(c).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) } }
        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val label = TextView(c).apply { this.text = "Ad"; setTextColor(sub); textSize = 10f }
        val headline = TextView(c).apply { setTextColor(text); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1 }
        val body = TextView(c).apply { setTextColor(sub); textSize = 12f; maxLines = 2 }
        // the button's text is black on a light accent (AMOLED and Win11 accents are white): otherwise the label is white on white
        val ctaText = android.graphics.Color.WHITE
        val cta = Button(c).apply { setTextColor(ctaText); setBackgroundColor(android.graphics.Color.BLACK); isAllCaps = false; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { leftMargin = dp(10) } }
        texts.addView(label); texts.addView(headline); texts.addView(body)
        row.addView(icon); row.addView(texts); row.addView(cta)
        root.addView(row)
        root.headlineView = headline
        root.bodyView = body
        root.iconView = icon
        root.callToActionView = cta
        return root
    }

    private fun bindNative(root: NativeAdView, ad: NativeAd) {
        (root.headlineView as TextView).text = ad.headline.orEmpty()
        (root.bodyView as TextView).text = ad.body.orEmpty()
        (root.callToActionView as Button).text = ad.callToAction.orEmpty()
        root.callToActionView?.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        ad.icon?.drawable?.let { (root.iconView as ImageView).setImageDrawable(it) }
        root.setNativeAd(ad)
    }

    private fun loadRewarded(c: Context) {
        RewardedAd.load(c, REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null }
        })
    }

    /**
     * Shows a rewarded ad. [onReward] only when it was watched to the end. [onFail] when no ad is ready, when it cannot be shown, or when it is
     * closed before it was watched to the end: no reward in any of those cases. The next ad loads at once.
     */
    fun showRewarded(a: Activity, onReward: () -> Unit, onFail: () -> Unit) {
        val ad = rewarded
        val app = a.applicationContext
        if (ad == null) { onFail(); loadRewarded(app); return }
        rewarded = null
        var earned = false
        // the reward is given once the ad is closed (the earned signal comes while the ad still shows; the action must not open over it)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { if (earned) onReward() else onFail(); loadRewarded(app) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { onFail(); loadRewarded(app) }
        }
        ad.show(a) { _ -> earned = true }
    }
}
