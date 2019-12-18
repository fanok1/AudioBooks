package com.fanok.audiobooks;

import android.content.Context;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.pojo.StorageAds;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

public class MyInterstitialAd {

    private static final int ADDS_SHOWING_COUNT = 3;
    private static int adsCount = 0;

    private static InterstitialAd mInterstitialAd;

    public static void create(@NonNull Context context) {
        if (!StorageAds.idDisableAds()) {
            mInterstitialAd = new InterstitialAd(context);
            mInterstitialAd.setAdUnitId(context.getResources().getString(R.string.interstitiaID));
            mInterstitialAd.loadAd(new AdRequest.Builder().build());
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    mInterstitialAd.loadAd(new AdRequest.Builder().build());
                }

            });
        }
    }

    public static void show() {
        if (!StorageAds.idDisableAds() && mInterstitialAd != null
                && adsCount % ADDS_SHOWING_COUNT == 0
                && mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
    }

    public static void showRequire() {
        if (!StorageAds.idDisableAds() && mInterstitialAd != null
                && mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
    }

    public static void increase() {
        adsCount++;
    }
}
