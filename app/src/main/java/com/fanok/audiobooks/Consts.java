package com.fanok.audiobooks;


import static android.content.Context.ACTIVITY_SERVICE;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PAUSE;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_NEXT;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_PREVIOUS;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_RESUME;

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
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import com.fanok.audiobooks.search.Provider;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocketFactory;

public class Consts {
    private static final String TAG = "Consts";
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^(https?://.+\\..+)|((content|file)://.+)$");

    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^(https?://.+\\.((jpg)|(png)|(jpeg)|(webp))(\\?.*)?)|((content|file)://.+)$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_URL_MP3 = Pattern.compile(
            "^https?://.+\\.mp3.*$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    // Built-in SOCKS proxy behind the Settings switch "Прокси", applied process-wide by net/AppProxy, which hands
    // these credentials only to this host:port. They are deliberately disposable, dedicated to this app (owner's
    // decision, 2026-10-23). Restored from commit 2f0a631; an empty host makes the switch inert.
    public static final String PROXY_HOST = "23.247.247.115";
    public static final int PROXY_PORT = 15441;
    public static final String PROXY_USER = "user142424";
    public static final String PROXY_PASSWORD = "3yuh5e";

    public static final String USER_AGENT
            = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";

    public static final int MODEL_BOOKS = 0;

    public static final int MODEL_GENRE = 1;

    public static final int MODEL_AUTOR = 2;

    public static final int MODEL_ARTIST = 3;

    public static final int TABLE_FAVORITE = 1;

    public static final int TABLE_HISTORY = 2;

    public static final int TABLE_SAVED = 3;

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

    public static final int FRAGMENT_SAVED = 8;

    public static final int IMPORT_SITE_KNIGA_V_UHE = 0;

    public static final int IMPORT_SITE_ABOOK = 1;

    public static final int SOURCE_KNIGA_V_UHE = 0;

    public static final int SOURCE_IZI_BUK = 1;

    public static final int SOURCE_AUDIO_BOOK_MP3 = 2;

    public static final int SOURCE_ABOOK = 3;

    public static final int SOURCE_BAZA_KNIG = 4;

    public static final int SOURCE_KNIGOBLUD = 5;

    public static final int SOURCE_AUDIOBOO= 6;

    public static final int SOURCE_OTRUB = 7;

    public static final int SOURCE_POLEKNIG = 8;

    private static int SOURCE;

    private static String bazaKnigCookies = "";


    public static int getSOURCE() {
        return SOURCE;
    }



    public static String getBazaKnigCookies() {
        return bazaKnigCookies;
    }
//plus_version | android.test.purchased

    public static void setBazaKnigCookies(final String bazaKnigCookies) {
        Consts.bazaKnigCookies = bazaKnigCookies;
    }

    /** Selects the source whose {@link Provider#prefValue} is {@code value} (the {@code sorce_books} preference). */
    public static void setSOURCE(@NonNull String value) {
        for (Provider provider : Provider.values()) {
            if (value.equals(provider.prefValue)) {
                Consts.SOURCE = provider.sourceId;
            }
        }
    }


    public static int getAttributeColor(Context context, int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getColor(colorRes);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Not found color resource by id: " + colorRes);
        }
        return color;
    }

    public static void setColorPrimeriTextInIconItemMenu(MenuItem item, @NonNull Context context) {
        Drawable drawable = item.getIcon();
        drawable = DrawableCompat.wrap(Objects.requireNonNull(drawable));
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

    /** The player command broadcast of a media key, or null: PLAY plays, PAUSE pauses, PLAY_PAUSE toggles. */
    @Nullable
    public static String mediaKeyBroadcast(int keycode) {
        switch (keycode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return Broadcast_RESUME;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return Broadcast_PAUSE;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return Broadcast_PLAY;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return Broadcast_PLAY_NEXT;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return Broadcast_PLAY_PREVIOUS;
            default:
                return null;
        }
    }

    /** Sends a media key to the player; @return true when the key is consumed (the play keys). */
    public static boolean handleUserInput(@NonNull Context context, int keycode) {
        Log.d(TAG, "Keycode " + keycode);
        String action = mediaKeyBroadcast(keycode);
        if (action == null) {
            return false;
        }
        context.sendBroadcast(new Intent(action).setPackage(context.getPackageName()));
        return !Broadcast_PLAY_NEXT.equals(action) && !Broadcast_PLAY_PREVIOUS.equals(action);
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

    /**
     * Platform TLS: system trust store plus the network_security_config anchors, with default
     * hostname verification. Never replace it with a trust-all factory.
     */
    public static SSLSocketFactory socketFactory() {
        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public static String formatTimeDisplay(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return "";
        timeStr = timeStr.trim();

        if (timeStr.matches("^\\d+:\\d{2}:\\d{2}$")) {
            String[] parts = timeStr.split(":");
            try {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                if (h == 0 && m == 0) return "0 ч. 0 мин.";
                if (h > 0 && m > 0) return h + " ч. " + m + " мин.";
                if (h > 0) return h + " ч.";
                return m + " мин.";
            } catch (Exception ignored) {}
        } else if (timeStr.matches("^\\d+:\\d{2}$")) {
            String[] parts = timeStr.split(":");
            try {
                int m = Integer.parseInt(parts[0]);
                return m + " мин.";
            } catch (Exception ignored) {}
        }

        String result = timeStr;
        result = result.replaceAll("(?i)\\b(\\d+)\\s*(?:часов|часа|час|ч)\\b\\.?", "$1 ч.");
        result = result.replaceAll("(?i)\\b(\\d+)\\s*(?:минуты|минут|минута|мин)\\b\\.?", "$1 мин.");
        result = result.replaceAll("(?i)\\b(\\d+)\\s*(?:секунды|секунд|секунда|сек)\\b\\.?", "");

        result = result.replace("часов", "ч.")
                       .replace("часа", "ч.")
                       .replace("час", "ч.")
                       .replace("минут", "мин.")
                       .replace("минуты", "мин.")
                       .replace("минута", "мин.");

        result = result.replaceAll("(\\d+)\\s*ч(?!\\.)", "$1 ч.");
        result = result.replaceAll("(\\d+)\\s*мин(?!\\.)", "$1 мин.");

        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }
    /**
     * True only when the device has no active network at all (airplane mode, nothing connected): then a failed load
     * of online content is not an error to report. A network without internet still counts as a network.
     */
    public static boolean isOffline(android.content.Context context) {
        if (context == null) return false;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getActiveNetwork() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isNetworkAvailable(android.content.Context context) {
        if (context == null) return true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.net.Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                        if (capabilities != null && (
                                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
                                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                        )) {
                            return true;
                        }
                    }
                }
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }
}
