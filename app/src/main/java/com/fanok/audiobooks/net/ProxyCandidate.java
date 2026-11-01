package com.fanok.audiobooks.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.Locale;

/**
 * One public proxy: an IPv4 host, a port and a type (HTTP or SOCKS5, no authentication), plus the result of its
 * last check. Identity is (host, port, type); the latency and check time do not take part in equality.
 */
public final class ProxyCandidate {

    /** Not checked yet. */
    public static final long UNCHECKED = -1;

    private final String host;
    private final int port;
    private final Proxy.Type type;
    private final long latencyMs;
    private final long checkedAt;

    public ProxyCandidate(@NonNull String host, int port, @NonNull Proxy.Type type) {
        this(host, port, type, UNCHECKED, 0);
    }

    ProxyCandidate(@NonNull String host, int port, @NonNull Proxy.Type type, long latencyMs, long checkedAt) {
        this.host = host;
        this.port = port;
        this.type = type;
        this.latencyMs = latencyMs;
        this.checkedAt = checkedAt;
    }

    @NonNull
    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    /** {@link Proxy.Type#HTTP} or {@link Proxy.Type#SOCKS} (SOCKS5). */
    @NonNull
    public Proxy.Type type() {
        return type;
    }

    /** Milliseconds the last check took, or {@link #UNCHECKED}. */
    public long latencyMs() {
        return latencyMs;
    }

    /** Wall-clock time of the last successful check, or 0. */
    public long checkedAt() {
        return checkedAt;
    }

    /** {@code host:port}, what the user sees. */
    @NonNull
    public String label() {
        return host + ":" + port;
    }

    /** "HTTP" or "SOCKS5". */
    @NonNull
    public String typeName() {
        return type == Proxy.Type.SOCKS ? "SOCKS5" : "HTTP";
    }

    @NonNull
    ProxyCandidate checked(long latencyMs, long checkedAt) {
        return new ProxyCandidate(host, port, type, latencyMs, checkedAt);
    }

    /** The proxy for a client; the address stays unresolved, so nothing is looked up here. */
    @NonNull
    public Proxy toProxy() {
        return new Proxy(type, InetSocketAddress.createUnresolved(host, port));
    }

    /** True when {@code address} (as a client reports a failed proxy) is this proxy's address. */
    boolean hasAddress(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress)) {
            return false;
        }
        InetSocketAddress inet = (InetSocketAddress) address;
        if (inet.getPort() != port) {
            return false;
        }
        if (host.equals(inet.getHostString())) {
            return true;
        }
        InetAddress resolved = inet.getAddress();
        return resolved != null && host.equals(resolved.getHostAddress());
    }

    /** "http" or "socks5", the stored form of the type. */
    @NonNull
    String typeKey() {
        return type == Proxy.Type.SOCKS ? "socks5" : "http";
    }

    /** The type of a stored or listed protocol name; null for anything but http/https/socks5. */
    @Nullable
    static Proxy.Type typeOf(@Nullable String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "http":
            case "https":
                return Proxy.Type.HTTP;
            case "socks5":
            case "socks5h":
                return Proxy.Type.SOCKS;
            default:
                return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProxyCandidate)) {
            return false;
        }
        ProxyCandidate other = (ProxyCandidate) o;
        return port == other.port && type == other.type && host.equals(other.host);
    }

    @Override
    public int hashCode() {
        return (host.hashCode() * 31 + port) * 31 + type.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return typeKey() + "://" + label() + (latencyMs >= 0 ? " " + latencyMs + "ms" : "");
    }
}
