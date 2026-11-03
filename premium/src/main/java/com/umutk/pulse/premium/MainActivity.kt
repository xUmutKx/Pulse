package com.umutk.pulse.premium

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Pulse Premium: one Google Play purchase (in-app product [PRODUCT_ID]). The screen shows whether it is owned.
 * Pulse itself checks this app's installation and signature, so the app must be published only on Google Play.
 */
class MainActivity : Activity(), PurchasesUpdatedListener {

    companion object {
        /** The one-time product created in Play Console (Monetize > Products > In-app products). */
        const val PRODUCT_ID = "pulse_premium"
    }

    private lateinit var billing: BillingClient
    private lateinit var info: TextView
    private lateinit var buy: Button
    private var product: ProductDetails? = null
    private var connected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        billing = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        connect()
    }

    override fun onDestroy() {
        if (::billing.isInitialized && billing.isReady) billing.endConnection()
        super.onDestroy()
    }

    private fun buildUi(): LinearLayout {
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Pulse Premium"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        info = TextView(this).apply {
            text = "…"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        }
        root.addView(info)
        buy = Button(this).apply {
            text = "…"
            isEnabled = false
            setOnClickListener { launchPurchase() }
        }
        root.addView(buy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun connect() {
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    show("Google Play is not available on this device.", enabled = false)
                    return
                }
                connected = true
                loadProduct()
                restore()
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            val item = details.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || item == null) {
                show("The product is not available yet.", enabled = false)
                return@queryProductDetailsAsync
            }
            product = item
            val price = item.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
            show("One purchase, every feature, no ads. $price".trim(), enabled = true)
        }
    }

    /** Asks Play whether this account already owns the product; nothing is stored on the phone. */
    private fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases.any(::owns)) {
                purchases.filter(::owns).forEach(::acknowledge)
                show("Owned. Pulse Premium is active.", enabled = false)
            }
        }
    }

    private fun launchPurchase() {
        val item = product ?: return
        if (!connected) return
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(item).build()
            ))
            .build()
        billing.launchBillingFlow(this, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.filter(::owns).forEach(::acknowledge)
            if (purchases.any(::owns)) show("Thank you. Pulse Premium is active.", enabled = false)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            show("The purchase did not finish. Nothing was charged.", enabled = true)
        }
    }

    private fun owns(p: Purchase) =
        p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(PRODUCT_ID)

    /** Play requires acknowledging a purchase within three days, otherwise it is refunded. */
    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        billing.acknowledgePurchase(params) { }
    }

    private fun show(text: String, enabled: Boolean) = runOnUiThread {
        info.text = text
        buy.text = if (enabled) "Buy once" else "OK"
        buy.isEnabled = enabled
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
