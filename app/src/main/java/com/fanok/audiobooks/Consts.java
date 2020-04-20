package com.fanok.audiobooks;


import static android.content.Context.ACTIVITY_SERVICE;

import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_NEXT;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_PREVIOUS;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Consts {
    private static final String TAG = "Consts";
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^https?://.+\\..+$");
    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^https?://.+\\.((jpg)|(png)|(jpeg))$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_URL_MP3 = Pattern.compile(
            "^https?://.+\\.mp3.*$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    public static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36";

    public static final int MODEL_BOOKS = 0;
    public static final int MODEL_GENRE = 1;
    public static final int MODEL_AUTOR = 2;
    public static final int MODEL_ARTIST = 3;

    public static final int TABLE_FAVORITE = 1;
    public static final int TABLE_HISTORY = 2;

    public static final int REQEST_CODE_SEARCH = 157;

    public static final String ARG_MODEL = "ARG_MODEL";

    public static final String DBName = "audioBooksDB";

    public static final double COLLAPS_BUTTON_VISIBLE = 0.7;
    public static final double COLLAPS_BUTTON_VISIBLE_STEP = (1 - Consts.COLLAPS_BUTTON_VISIBLE);


    public static final int FRAGMENT_AUDIOBOOK = 0;
    public static final int FRAGMENT_GENRE = 1;
    public static final int FRAGMENT_AUTOR = 2;
    public static final int FRAGMENT_ARTIST = 3;
    public static final int FRAGMENT_FAVORITE = 4;
    public static final int FRAGMENT_HISTORY = 5;
    public static final int LAST_BOOK = 6;
    public static final int FRAGMENT_SETTINGS = 7;

    public static final int IMPORT_SITE_KNIGA_V_UHE = 0;
    public static final int SOURCE_KNIGA_V_UHE = 0;
    public static final int SOURCE_IZI_BUK = 1;
    public static boolean izibuk_reiting = false;
    private static int SOURCE;

    public static int getSOURCE() {
        return SOURCE;
    }

    public static void setSOURCE(@NonNull Context context, @NonNull String value) {
        if (value.equals(context.getString(R.string.kniga_v_uhe_value))) {
            Consts.SOURCE = Consts.SOURCE_KNIGA_V_UHE;
        } else if (value.equals(context.getString(R.string.izibuc_value))) {
            Consts.SOURCE = Consts.SOURCE_IZI_BUK;
        }
    }

    public static final String mSkuId = "android.test.purchased";
//plus_version | android.test.purchased



    public static int getAttributeColor(Context context, int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getResources().getColor(colorRes);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Not found color resource by id: " + colorRes);
        }
        return color;
    }

    public static void setColorPrimeriTextInIconItemMenu(MenuItem item, @NonNull Context context) {
        Drawable drawable = item.getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable,
                Consts.getAttributeColor(context, R.attr.colorItemMenu));
        item.setIcon(drawable);
    }

    public static int indexOfByNumber(@NonNull String str, char c, int index) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
                if (count == index) return i;
            }
        }
        return -1;
    }

    public static boolean handleUserInput(@NonNull Context context, int keycode) {
        Log.d(TAG, "Keycode " + keycode);
        Intent broadcastIntent;
        switch (keycode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                broadcastIntent = new Intent(Broadcast_PLAY);
                context.sendBroadcast(broadcastIntent);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
                context.sendBroadcast(broadcastIntent);
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
                context.sendBroadcast(broadcastIntent);
                break;
            default:
        }

        return false;
    }

    public static boolean isServiceRunning(@NonNull Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(
                    Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static SSLSocketFactory socketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create a SSL socket factory", e);
        }
    }
}
