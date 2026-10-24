package com.fanok.audiobooks.net;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The built-in SOCKS proxy behind the "Прокси" switch ({@link #PREF}). Installed once as the process-wide
 * {@link ProxySelector}, so every HTTP path (site pages through SiteHttp, audio and downloads through OkHttp,
 * covers) follows the switch without per-request code; the system proxy keeps working while the switch is off.
 * The credentials are handed only to that proxy host:port, never to a site that asks for authentication.
 */
public final class AppProxy {

    public static final String PREF = "pref_proxy";

    private static volatile boolean enabled;
    /** Strong reference: SharedPreferences keeps its listeners weakly. */
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private AppProxy() {
    }

    /** True when this build carries proxy values; without them the switch is inert. */
    public static boolean configured() {
        return !Consts.PROXY_HOST.isEmpty() && Consts.PROXY_PORT > 0;
    }

    public static boolean isEnabled() {
        return enabled && configured();
    }

    /** Call once, before the first HTTP client is built (OkHttp captures the default selector at construction). */
    public static void install(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        enabled = readSwitch(prefs);
        listener = (p, key) -> {
            if (PREF.equals(key)) {
                enabled = readSwitch(p);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        ProxySelector.setDefault(new Selector(ProxySelector.getDefault(), AppProxy::isEnabled, proxy()));
        Authenticator.setDefault(new ProxyOnlyAuthenticator(Consts.PROXY_HOST, Consts.PROXY_PORT,
                Consts.PROXY_USER, Consts.PROXY_PASSWORD));
    }

    /** DATA-12: an older version stored this key as a string; such a value means "off" and is dropped. */
    static boolean readSwitch(@NonNull SharedPreferences prefs) {
        try {
            return prefs.getBoolean(PREF, false);
        } catch (ClassCastException e) {
            prefs.edit().remove(PREF).apply();
            return false;
        }
    }

    static Proxy proxy() {
        return new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(Consts.PROXY_HOST, Math.max(Consts.PROXY_PORT, 1)));
    }

    /** Routes web traffic through the proxy while the switch is on; otherwise defers to the system selector. */
    static final class Selector extends ProxySelector {
        private final ProxySelector fallback;
        private final BooleanSupplier on;
        private final Proxy proxy;

        Selector(@Nullable ProxySelector fallback, @NonNull BooleanSupplier on, @NonNull Proxy proxy) {
            this.fallback = fallback;
            this.on = on;
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(URI uri) {
            String scheme = uri == null ? null : uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "socket".equalsIgnoreCase(scheme);
            if (web && on.getAsBoolean()) {
                return Collections.singletonList(proxy);
            }
            return fallback != null ? fallback.select(uri) : Collections.singletonList(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            if (fallback != null) {
                fallback.connectFailed(uri, sa, ioe);
            }
        }
    }

    /** Answers only the proxy's own challenge (SEC-03: the old global authenticator answered every challenger). */
    static final class ProxyOnlyAuthenticator extends Authenticator {
        private final String host;
        private final int port;
        private final String user;
        private final String password;

        ProxyOnlyAuthenticator(String host, int port, String user, String password) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (user.isEmpty() || !host.equalsIgnoreCase(getRequestingHost()) || getRequestingPort() != port) {
                return null;
            }
            return new PasswordAuthentication(user, password.toCharArray());
        }
    }
}
