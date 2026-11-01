package com.fanok.audiobooks.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the automatic proxy finder takes its candidates from, and how each list is read. {@link #ALL} is a plain
 * table, one line per list: edit it freely. Only IPv4 HTTP and SOCKS5 proxies are kept; SOCKS4 and IPv6 are skipped.
 */
public final class ProxySources {

    /** How a list is written. */
    public enum Format {
        /** One {@code ip:port} per line; anything after the port ({@code :country}, spaces) is ignored. */
        IPPORT,
        /** One {@code http://ip:port}, {@code socks5://ip:port} (or socks4, skipped) per line. */
        PROTOIPPORT,
        /** geonode JSON: {@code {"data":[{"ip":..,"port":..,"protocols":[..]}]}}. No list in {@link #ALL} uses it now. */
        GEONODE
    }

    /** One list. {@code type} is the proxy type of every line of an {@link Format#IPPORT} list; null for mixed lists. */
    public static final class Source {
        public final String name;
        public final String url;
        public final Format format;
        @Nullable
        public final Proxy.Type type;

        public Source(@NonNull String name, @NonNull String url, @NonNull Format format, @Nullable Proxy.Type type) {
            this.name = name;
            this.url = url;
            this.format = format;
            this.type = type;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Proxy.Type HTTP = Proxy.Type.HTTP;
    private static final Proxy.Type SOCKS5 = Proxy.Type.SOCKS;
    private static final Proxy.Type MIXED = null;

    /**
     * The lists, one line each (name, URL, format, type of an ipport list). Measured 2026-10-28 against
     * https://akniga.org/: the share of entries that answered, and the list size. Small checked lists yield 3-10x
     * more than big dumps; dropped for low yield: TheSpeedX (5-7 %), proxifly all (0.3 %), hookzof (2 %), geonode (13 %).
     */
    public static final List<Source> ALL = Collections.unmodifiableList(Arrays.asList(
            // 23-26 % alive, 55 KB, API; socks4 lines are skipped
            new Source("proxyscrape", "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&proxy_format=protocolipport&format=text", Format.PROTOIPPORT, MIXED),
            // 39-44 %, 5 KB; lines are ip:port:Country, the trailing field is ignored
            new Source("zloi-socks5", "https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks5.txt", Format.IPPORT, SOCKS5),
            // 17-19 %, 6 KB
            new Source("zloi-http", "https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt", Format.IPPORT, HTTP),
            // 23-27 %, 9 KB
            new Source("monosans-socks5", "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt", Format.IPPORT, SOCKS5),
            // 16-19 %, 8 KB
            new Source("monosans-http", "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt", Format.IPPORT, HTTP),
            // 22 %, 5 KB
            new Source("elliottophellia-http", "https://raw.githubusercontent.com/elliottophellia/proxylist/master/results/http/global/http_checked.txt", Format.IPPORT, HTTP),
            // 16-20 %, 13 KB
            new Source("elliottophellia-socks5", "https://raw.githubusercontent.com/elliottophellia/proxylist/master/results/socks5/global/socks5_checked.txt", Format.IPPORT, SOCKS5),
            // 26-28 %, 38 KB
            new Source("aliilapro-socks5", "https://raw.githubusercontent.com/ALIILAPRO/Proxy/main/socks5.txt", Format.IPPORT, SOCKS5),
            // 25-27 %, 10 KB
            new Source("themiralay-http", "https://raw.githubusercontent.com/themiralay/Proxy-List-World/master/data.txt", Format.IPPORT, HTTP)
    ));

    private static final Pattern IPPORT = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{1,5})(?:\\D.*)?$");
    private static final Pattern PROTOIPPORT = Pattern.compile(
            "^([A-Za-z0-9]+)://(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{1,5})(?:\\D.*)?$");

    private ProxySources() {
    }

    /** The candidates of one list's text; malformed lines are skipped, never an error. */
    @NonNull
    public static List<ProxyCandidate> parse(@NonNull Source source, @Nullable String body) {
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }
        switch (source.format) {
            case IPPORT:
                return source.type == null ? Collections.emptyList() : parseIpPort(body, source.type);
            case PROTOIPPORT:
                return parseProtoIpPort(body);
            case GEONODE:
                return parseGeonode(body);
            default:
                return Collections.emptyList();
        }
    }

    @NonNull
    static List<ProxyCandidate> parseIpPort(@NonNull String body, @NonNull Proxy.Type type) {
        Set<ProxyCandidate> out = new LinkedHashSet<>();
        for (String raw : body.split("\r?\n|\r")) {
            Matcher m = IPPORT.matcher(raw.trim());
            if (m.matches()) {
                add(out, m.group(1), m.group(2), type);
            }
        }
        return new ArrayList<>(out);
    }

    @NonNull
    static List<ProxyCandidate> parseProtoIpPort(@NonNull String body) {
        Set<ProxyCandidate> out = new LinkedHashSet<>();
        for (String raw : body.split("\r?\n|\r")) {
            Matcher m = PROTOIPPORT.matcher(raw.trim());
            if (m.matches()) {
                Proxy.Type type = ProxyCandidate.typeOf(m.group(1));
                if (type != null) {
                    add(out, m.group(2), m.group(3), type);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Streams the geonode records, so a body cut at the size limit still yields every record read before the cut.
     * A record lists its protocols; it becomes one candidate per supported protocol.
     */
    @NonNull
    static List<ProxyCandidate> parseGeonode(@NonNull String body) {
        Set<ProxyCandidate> out = new LinkedHashSet<>();
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                return new ArrayList<>(out);
            }
            reader.beginObject();
            while (reader.hasNext()) {
                if (!"data".equals(reader.nextName()) || reader.peek() != JsonToken.BEGIN_ARRAY) {
                    reader.skipValue();
                    continue;
                }
                reader.beginArray();
                while (reader.hasNext()) {
                    readGeonodeRecord(reader, out);
                }
                reader.endArray();
            }
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            // truncated or malformed: keep what was read
        }
        return new ArrayList<>(out);
    }

    private static void readGeonodeRecord(JsonReader reader, Set<ProxyCandidate> out) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return;
        }
        String ip = null;
        String port = null;
        List<String> protocols = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            JsonToken token = reader.peek();
            if ("ip".equals(name) && token == JsonToken.STRING) {
                ip = reader.nextString();
            } else if ("port".equals(name) && (token == JsonToken.STRING || token == JsonToken.NUMBER)) {
                port = reader.nextString();
            } else if ("protocols".equals(name) && token == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.STRING) {
                        protocols.add(reader.nextString());
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        for (String protocol : protocols) {
            Proxy.Type type = ProxyCandidate.typeOf(protocol);
            if (type != null && ip != null && port != null) {
                add(out, ip.trim(), port.trim(), type);
            }
        }
    }

    private static void add(Set<ProxyCandidate> out, String host, String port, Proxy.Type type) {
        if (!isPublicIpv4(host)) {
            return;
        }
        int p;
        try {
            p = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return;
        }
        if (p < 1 || p > 65535) {
            return;
        }
        out.add(new ProxyCandidate(canonicalIpv4(host), p, type));
    }

    /**
     * A dotted IPv4 literal of a public address. Names, IPv6 and local addresses (this network, loopback, private,
     * shared, link-local, multicast and reserved) are not: a list must not make the app probe the user's own network.
     * Nor are the other non-global special-purpose ranges (IETF protocol assignments, documentation, benchmarking).
     */
    static boolean isPublicIpv4(@Nullable String host) {
        if (host == null) {
            return false;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int n = 0; n < 4; n++) {
            String part = parts[n];
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                    return false;
                }
            }
            octets[n] = Integer.parseInt(part);
            if (octets[n] > 255) {
                return false;
            }
        }
        int a = octets[0];
        int b = octets[1];
        int c = octets[2];
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254)
                && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && b == 168)
                && !(a == 192 && b == 0 && (c == 0 || c == 2))
                && !(a == 198 && (b == 18 || b == 19))
                && !(a == 198 && b == 51 && c == 100)
                && !(a == 203 && b == 0 && c == 113);
    }

    /** "010.001.002.003" and "10.1.2.3" are one proxy. */
    private static String canonicalIpv4(String host) {
        String[] parts = host.split("\\.");
        return String.format(Locale.ROOT, "%d.%d.%d.%d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    /**
     * One scan's order: {@code first} (the proxies that worked last time) as given, then the lists shuffled each and
     * taken round-robin, so one huge list cannot crowd out the others. Duplicates (host, port, type) are dropped and
     * the result is cut at {@code cap}.
     */
    @NonNull
    public static List<ProxyCandidate> interleave(@NonNull List<ProxyCandidate> first,
            @NonNull List<List<ProxyCandidate>> lists, int cap, @NonNull Random random) {
        Set<ProxyCandidate> out = new LinkedHashSet<>();
        for (ProxyCandidate candidate : first) {
            if (out.size() >= cap) {
                return new ArrayList<>(out);
            }
            out.add(candidate);
        }
        List<List<ProxyCandidate>> shuffled = new ArrayList<>();
        for (List<ProxyCandidate> list : lists) {
            if (list != null && !list.isEmpty()) {
                List<ProxyCandidate> copy = new ArrayList<>(list);
                Collections.shuffle(copy, random);
                shuffled.add(copy);
            }
        }
        Collections.shuffle(shuffled, random);
        int[] next = new int[shuffled.size()];
        boolean more = true;
        while (more && out.size() < cap) {
            more = false;
            for (int i = 0; i < shuffled.size() && out.size() < cap; i++) {
                List<ProxyCandidate> list = shuffled.get(i);
                if (next[i] < list.size()) {
                    out.add(list.get(next[i]++));
                    more = true;
                }
            }
        }
        return new ArrayList<>(out);
    }
}
