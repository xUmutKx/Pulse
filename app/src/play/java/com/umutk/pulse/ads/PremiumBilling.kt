package com.umutk.pulse.ads

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Pulse Premium inside the app (play build only): one Google Play purchase of the product [PRODUCT_ID].
 * Play confirms the purchase on every start ([restore]); nothing is stored on the phone. [Premium.on] reads the result for this run.
 */
object PremiumBilling {
    const val PRODUCT_ID = "pulse_premium"
    private var client: BillingClient? = null
    private var product: ProductDetails? = null
    private var pending: ((Boolean) -> Unit)? = null

    private fun billing(c: Context): BillingClient {
        client?.let { return it }
        val app = c.applicationContext
        val b = BillingClient.newBuilder(app)
            .setListener { result, purchases -> onUpdate(app, result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = b
        return b
    }

    /** Connects to Play; [done] gets true when the connection is ready. */
    private fun connect(c: Context, done: (Boolean) -> Unit) {
        val b = billing(c)
        if (b.isReady) { done(true); return }
        b.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                done(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() { }
        })
    }

    /** Asks Play whether this Google account owns Premium, so a reinstall or a new phone gets it back. Runs at start. */
    fun restore(c: Context, done: (Boolean) -> Unit = { }) {
        connect(c) { ok ->
            if (!ok) { done(Premium.on(c)); return@connect }
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            billing(c).queryPurchasesAsync(params) { result, purchases ->
                // a failed query keeps what is saved; a good answer sets the flag either way
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val has = purchases.any(::owns)
                    if (has) acknowledge(billing(c), purchases.filter(::owns))
                    Premium.verified = has
                }
                done(Premium.on(c))
            }
        }
    }

    /** Opens the Play purchase sheet for Premium; [done] gets true once it is bought and acknowledged. */
    fun buy(a: Activity, done: (Boolean) -> Unit) {
        connect(a) { ok ->
            if (!ok) { done(false); return@connect }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
                ))
                .build()
            billing(a).queryProductDetailsAsync(params) { result, details ->
                val item = details.firstOrNull()
                if (result.responseCode != BillingClient.BillingResponseCode.OK || item == null) { done(false); return@queryProductDetailsAsync }
                product = item
                pending = done
                val flow = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(item).build()
                    ))
                    .build()
                billing(a).launchBillingFlow(a, flow)
            }
        }
    }

    private fun onUpdate(c: Context, result: BillingResult, purchases: MutableList<Purchase>?) {
        val mine = purchases.orEmpty().filter(::owns)
        if (result.responseCode == BillingClient.BillingResponseCode.OK && mine.isNotEmpty()) {
            acknowledge(billing(c), mine)
            Premium.verified = true
        }
        pending?.invoke(Premium.on(c))
        pending = null
    }

    private fun owns(p: Purchase) =
        p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(PRODUCT_ID)

    /** Play refunds a purchase that is not acknowledged within three days. */
    private fun acknowledge(b: BillingClient, list: List<Purchase>) {
        list.filter { !it.isAcknowledged }.forEach { p ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            b.acknowledgePurchase(params) { }
        }
    }
}
