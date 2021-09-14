package com.meltingb.inapppurchase

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * 결제 Callback 인터페이스
 */
interface BillingCallback {
    fun onBillingConnected() // BillingClient 연결 성공 시 호출
    fun onSuccess(purchase: Purchase) // 구매 성공 시 호출 Purchase : 구매정보
    fun onFailure(responseCode: Int) // 구매 실패 시 호출 errorCode : BillingResponseCode
}

class BillingManager(private val activity: Activity, private val callback: BillingCallback) {

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                confirmPurchase(purchase)
            }
        } else {
            callback.onFailure(billingResult.responseCode)
        }
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.d("BillingManager", "== BillingClient onBillingServiceDisconnected() called ==")
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    callback.onBillingConnected()
                } else {
                    callback.onFailure(billingResult.responseCode)
                }
            }
        })
    }

    /**
     * 콘솔에 등록한 상품 리스트를 가져온다.
     * @param sku 상품 ID String
     * @param billingType String IN_APP or SUBS
     * @param resultBlock 결과로 받을 상품정보들에 대한 처리
     */
    fun getSkuDetails(
        vararg sku: String,
        billingType: String,
        resultBlock: (List<SkuDetails>) -> Unit = {}
    ) {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(sku.asList())
            .setType(billingType)

        billingClient.querySkuDetailsAsync(params.build()) { _, list ->
            CoroutineScope(Dispatchers.Main).launch {
                resultBlock(list ?: emptyList())
            }
        }
    }

    /**
     * 구매 시도
     * @param skuDetail SkuDetails 구매 할 상품
     */
    fun purchaseSku(skuDetail: SkuDetails) {
        val flowParams = BillingFlowParams.newBuilder().apply {
            setSkuDetails(skuDetail)
        }.build()

        val responseCode = billingClient.launchBillingFlow(activity, flowParams).responseCode
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            callback.onFailure(responseCode)
        }
    }

    /**
     * 구독 여부 확인
     * @param sku String 구매 확인 상품
     * @param resultBlock 구매 확인 상품에 대한 처리 return Purchase
     */
    fun checkSubscribed(sku: String, resultBlock: (Purchase?) -> Unit) {
        billingClient.queryPurchasesAsync(sku) { _, purchases ->
            CoroutineScope(Dispatchers.Main).launch {
                for (purchase in purchases) {
                    if (purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        return@launch resultBlock(purchase)
                    }
                }
                return@launch resultBlock(null)
            }
        }
    }

    /**
     * 구매 확인
     * @param purchase
     */
    fun confirmPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            // 구매를 완료 했지만 확인이 되지 않은 경우 확인 처리
            val ackPurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)

            CoroutineScope(Dispatchers.IO).launch {
                billingClient.acknowledgePurchase(ackPurchaseParams.build()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                            callback.onSuccess(purchase)
                        } else {
                            callback.onFailure(it.responseCode)
                        }
                    }
                }
            }
        }
    }

    /**
     * 구매 확인이 안 된 경우 다시 확인 할 수 있도록
     */
    fun onResume(type: String) {
        if (billingClient.isReady) {
            billingClient.queryPurchasesAsync(type) { _, purchases ->
                for (purchase in purchases) {
                    if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        confirmPurchase(purchase)
                    }
                }
            }
        }
    }


}