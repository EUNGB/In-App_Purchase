package com.meltingb.inapppurchase

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var manager: BillingManager
    val subsItemID = "subs_item"

    private var mSkuDetails = listOf<SkuDetails>()
        set(value) {
            field = value
            getSkuDetails()
        }

    private var currentSubscription: Purchase? = null
        set(value) {
            field = value
            updateSubscriptionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manager = BillingManager(this, object : BillingCallback {
            override fun onBillingConnected() {
                manager.getSkuDetails(subsItemID, billingType = BillingClient.SkuType.SUBS) { list ->
                    mSkuDetails = list
                }

                manager.checkSubscribed(subsItemID) {
                    currentSubscription = it
                }
            }

            override fun onSuccess(purchase: Purchase) {
                currentSubscription = purchase
            }

            override fun onFailure(responseCode: Int) {
                Toast.makeText(
                    applicationContext,
                    "구매 도중 오류가 발생하였습니다. (${responseCode})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        btn_purchase.setOnClickListener {
            mSkuDetails.find { it.sku == subsItemID }?.let { skuDetail ->
                manager.purchaseSku(skuDetail)
            } ?: also {
                Toast.makeText(this, "구매 가능 한 상품이 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getSkuDetails() {
        var info = ""
        for (skuDetail in mSkuDetails) {
            info += "${skuDetail.title}, ${skuDetail.price} \n"
        }
        Toast.makeText(this, info, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateSubscriptionState() {
        currentSubscription?.let {
            tv_state.text = "구독중: ${it.skus} "
        } ?: also {
            tv_state.text = "구독권이 없습니다."
        }
    }
}