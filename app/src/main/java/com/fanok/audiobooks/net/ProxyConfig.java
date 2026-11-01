package com.fanok.audiobooks.net;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.Consts;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Locale;

/**
 * "Свой прокси": the server of the built-in proxy mode ({@link AppProxy#MODE_BUILTIN}) — its type, address and
 * optional login. Each value the user never set is the build's own ({@link Consts}), so an untouched install keeps
 * today's server and a value is stored only once the user saves the dialog. The password is never shown when it is the
 * build's ({@link #ownPassword}).
 */
public final class ProxyConfig {

    public static final String PREF_TYPE = "pref_proxy_type";
    public static final String PREF_HOST = "pref_proxy_host";
    public static final String PREF_PORT = "pref_proxy_port";
    public static final String PREF_USER = "pref_proxy_user";
    public static final String PREF_PASSWORD = "pref_proxy_password";

    /** A SOCKS5 proxy, or an HTTP one (it tunnels https sites with CONNECT); an HTTPS proxy is not supported. */
    public static final String TYPE_SOCKS = "socks5";
    public static final String TYPE_HTTP = "http";

    /** The field the user must fix, for {@link #check}. */
    public enum Problem { NONE, HOST, PORT, USER }

    @NonNull
    public final String type;
    @NonNull
    public final String host;
    /** 0 when not set. */
    public final int port;
    @NonNull
    public final String user;
    @NonNull
    public final String password;
    /** The password is the build's ({@link Consts#PROXY_PASSWORD}): the user never typed one. */
    public final boolean ownPassword;

    public ProxyConfig(@NonNull String type, @NonNull String host, int port, @NonNull String user,
            @NonNull String password, boolean ownPassword) {
        this.type = TYPE_HTTP.equals(type) ? TYPE_HTTP : TYPE_SOCKS;
        this.host = host.trim();
        this.port = port;
        this.user = user;
        this.password = ownPassword ? Consts.PROXY_PASSWORD : password;
        this.ownPassword = ownPassword;
    }

    /** The build's own server, as before the setting existed. */
    @NonNull
    public static ProxyConfig builtIn() {
        return new ProxyConfig(TYPE_SOCKS, Consts.PROXY_HOST, Consts.PROXY_PORT, Consts.PROXY_USER, "", true);
    }

    /** The stored values; a missing or wrongly typed one is the build's. */
    @NonNull
    public static ProxyConfig read(@NonNull SharedPreferences prefs) {
        ProxyConfig own = builtIn();
        String password = string(prefs, PREF_PASSWORD, null);
        int port;
        try {
            port = prefs.getInt(PREF_PORT, own.port);
        } catch (ClassCastException e) {
            port = own.port;
        }
        return new ProxyConfig(string(prefs, PREF_TYPE, own.type), string(prefs, PREF_HOST, own.host), port,
                string(prefs, PREF_USER, own.user), password == null ? "" : password, password == null);
    }

    /** Stores every value; the build's password stays unstored, so it is never read back as the user's own. */
    public void save(@NonNull SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_TYPE, type)
                .putString(PREF_HOST, host)
                .putInt(PREF_PORT, port)
                .putString(PREF_USER, user);
        if (ownPassword) {
            editor.remove(PREF_PASSWORD);
        } else {
            editor.putString(PREF_PASSWORD, password);
        }
        editor.apply();
    }

    /** Back to the build's server: every stored value goes. */
    public static void reset(@NonNull SharedPreferences prefs) {
        prefs.edit().remove(PREF_TYPE).remove(PREF_HOST).remove(PREF_PORT).remove(PREF_USER).remove(PREF_PASSWORD)
                .apply();
    }

    /** A server is set: an address and a port. */
    public boolean usable() {
        return check(host, port, user, password) == Problem.NONE;
    }

    /** The route to this server; the address stays unresolved, so the proxy resolves the sites' names. */
    @NonNull
    public Proxy toProxy() {
        return new Proxy(TYPE_HTTP.equals(type) ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(host.isEmpty() ? "localhost" : host, Math.max(port, 1)));
    }

    /** True when this is the build's own server, the only one its login belongs to. */
    public boolean isBuiltInServer() {
        ProxyConfig own = builtIn();
        return own.isAt(host, port);
    }

    /** True when {@code host}:{@code port} is this server (the one to give the login to). */
    public boolean isAt(@Nullable String host, int port) {
        return host != null && !this.host.isEmpty() && this.host.equalsIgnoreCase(host) && this.port == port;
    }

    /** {@code type://host:port}, for the settings. */
    @NonNull
    public String label() {
        return type + "://" + host + ":" + port;
    }

    /**
     * Pure: what is wrong with the values, the first field first. A host name or IP address, a port 1–65535, and a
     * login whenever there is a password (SOCKS5 and HTTP both send them as a pair).
     */
    @NonNull
    public static Problem check(@NonNull String host, int port, @NonNull String user, @NonNull String password) {
        String h = host.trim();
        // a name or IPv4 address, or an IPv6 one without brackets; never a scheme, a login or a port of its own
        boolean name = h.matches("[A-Za-z0-9._-]+");
        boolean ipv6 = h.matches("[0-9A-Fa-f:.]+") && h.indexOf(':') != h.lastIndexOf(':');
        if (!name && !ipv6) {
            return Problem.HOST;
        }
        if (port < 1 || port > 65535) {
            return Problem.PORT;
        }
        if (user.trim().isEmpty() && !password.isEmpty()) {
            return Problem.USER;
        }
        return Problem.NONE;
    }

    /** Pure: the port typed in a field; 0 for anything that is not a number. */
    public static int port(@Nullable String text) {
        try {
            return text == null ? 0 : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String string(@NonNull SharedPreferences prefs, @NonNull String key, @Nullable String fallback) {
        try {
            return prefs.getString(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxyConfig)) {
            return false;
        }
        ProxyConfig that = (ProxyConfig) o;
        return type.equals(that.type) && host.toLowerCase(Locale.ROOT).equals(that.host.toLowerCase(Locale.ROOT))
                && port == that.port && user.equals(that.user) && password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return (type + host.toLowerCase(Locale.ROOT) + port + user).hashCode();
    }
}
