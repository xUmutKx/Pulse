package com.umutk.pulse.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** The ads of the play build: a banner, and a rewarded ad that unlocks a thing when it is watched to the end. */
object PulseAds {
    const val ENABLED = true
    private const val BANNER = "ca-app-pub-2608227008506779/9168342683"
    private const val REWARDED = "ca-app-pub-2608227008506779/8920930672"
    private var rewarded: RewardedAd? = null

    fun init(c: Context) {
        MobileAds.initialize(c) { }
        loadRewarded(c)
    }

    /** A standard banner at the bottom of a screen; it is released when it leaves the screen. */
    @Composable
    fun Banner(modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx -> AdView(ctx).apply { setAdSize(AdSize.BANNER); adUnitId = BANNER; loadAd(AdRequest.Builder().build()) } },
            onRelease = { it.destroy() },
        )
    }

    private fun loadRewarded(c: Context) {
        RewardedAd.load(c, REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null }
        })
    }

    /** Shows a rewarded ad when one is ready: [onReward] after it is watched to the end, [onNone] when none is ready (the next one loads). */
    fun showRewarded(a: Activity, onReward: () -> Unit, onNone: () -> Unit) {
        val ad = rewarded
        if (ad == null) { onNone(); loadRewarded(a); return }
        rewarded = null
        ad.show(a) { _ -> onReward() }
        loadRewarded(a)
    }
}
