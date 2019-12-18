package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.mSkuId;
import static com.fanok.audiobooks.activity.MainActivity.Broadcast_DISABLE_ADS;
import static com.fanok.audiobooks.activity.PopupGetPlus.Broadcast_RECREATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import com.fanok.audiobooks.pojo.StorageAds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Billing {

    private static BillingClient mBillingClient;
    private static Map<String, SkuDetails> mSkuDetailsMap = new HashMap<>();

    static void initBilding(Context context) {
        mBillingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener(
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && purchases != null) {
                        payComplete(context, true);
                    }

                }).build();
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    querySkuDetails();

                    List<Purchase> purchasesList = queryPurchases(); //запрос о покупках

                    //если товар уже куплен, предоставить его пользователю
                    boolean temp = false;
                    for (int i = 0; i < purchasesList.size(); i++) {
                        String purchaseId = purchasesList.get(i).getSku();
                        if (TextUtils.equals(mSkuId, purchaseId)) {
                            temp = true;
                            break;
                        }
                    }
                    payComplete(context, temp);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Toast.makeText(context, context.getString(R.string.error_buy),
                        Toast.LENGTH_SHORT).show();
            }


        });
    }

    private static void payComplete(Context context, boolean b) {
        StorageAds.setDisableAds(b);
        Intent broadcastIntent = new Intent(Broadcast_DISABLE_ADS);
        context.sendBroadcast(broadcastIntent);
        if (b) {
            context.sendBroadcast(new Intent(Broadcast_RECREATE));
        } else {
            MyInterstitialAd.create(context);
        }
    }

    private static List<Purchase> queryPurchases() {
        Purchase.PurchasesResult purchasesResult = mBillingClient.queryPurchases(
                BillingClient.SkuType.INAPP);
        return purchasesResult.getPurchasesList();
    }

    private static void querySkuDetails() {
        SkuDetailsParams.Builder skuDetailsParamsBuilder = SkuDetailsParams.newBuilder();
        List<String> skuList = new ArrayList<>();
        skuList.add(mSkuId);
        skuDetailsParamsBuilder.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
        mBillingClient.querySkuDetailsAsync(skuDetailsParamsBuilder.build(),
                (billingResult, skuDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (SkuDetails skuDetails : skuDetailsList) {
                            mSkuDetailsMap.put(skuDetails.getSku(), skuDetails);
                        }
                    }
                });
    }

    public static void launchBilling(Activity activity, String skuId) {
        if (mBillingClient != null) {
            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(mSkuDetailsMap.get(skuId))
                    .build();
            mBillingClient.launchBillingFlow(activity, billingFlowParams);
        }
    }


}
