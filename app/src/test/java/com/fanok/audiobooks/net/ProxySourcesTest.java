package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class ProxySourcesTest {

    private static ProxyCandidate http(String host, int port) {
        return new ProxyCandidate(host, port, Proxy.Type.HTTP);
    }

    private static ProxyCandidate socks(String host, int port) {
        return new ProxyCandidate(host, port, Proxy.Type.SOCKS);
    }

    @Test
    public void ipPortLinesKeepPublicIpv4AndIgnoreTrailingFields() {
        String body = "1.2.3.4:8080\r\n"
                + "  5.6.7.8:3128:US  \n"
                + "8.8.8.8:80 elite proxy\n"
                + "# a comment\n"
                + "\n"
                + "junk\n"
                + "9.9.9.9:99999\n"
                + "9.9.9.9:0\n"
                + "300.1.1.1:80\n"
                + "[2001:db8::1]:8080\n"
                + "2001:db8::1:8080\n"
                + "10.0.0.1:80\n"
                + "192.168.1.1:8080\n"
                + "127.0.0.1:1080\n"
                + "proxy.example.com:8080\n"
                + "1.2.3.4:8080\n"
                + "001.002.003.004:8080\n";
        List<ProxyCandidate> parsed = ProxySources.parseIpPort(body, Proxy.Type.SOCKS);
        assertEquals(Arrays.asList(socks("1.2.3.4", 8080), socks("5.6.7.8", 3128), socks("8.8.8.8", 80)), parsed);
    }

    @Test
    public void protocolLinesTakeTheirTypeFromTheLine() {
        String body = "http://1.2.3.4:80\n"
                + "socks5://5.6.7.8:1080\n"
                + "socks4://9.9.9.9:1080\n"
                + "https://11.12.13.14:443\n"
                + "ftp://11.12.13.15:21\n"
                + "socks5://[::1]:1080\n"
                + "http://1.2.3.4:80:RU\n"
                + "1.2.3.4:8080\n"
                + "garbage\n";
        assertEquals(Arrays.asList(http("1.2.3.4", 80), socks("5.6.7.8", 1080), http("11.12.13.14", 443)),
                ProxySources.parseProtoIpPort(body));
    }

    @Test
    public void geonodeRecordsBecomeOneCandidatePerSupportedProtocol() {
        String body = "{\"data\":["
                + "{\"_id\":\"x\",\"ip\":\"1.2.3.4\",\"port\":\"8080\",\"protocols\":[\"http\"],\"country\":\"DE\"},"
                + "{\"ip\":\"5.6.7.8\",\"port\":1080,\"protocols\":[\"socks4\",\"socks5\"],\"anonymityLevel\":\"elite\"},"
                + "{\"ip\":\"2001:db8::1\",\"port\":\"80\",\"protocols\":[\"http\"]},"
                + "{\"ip\":\"9.9.9.9\",\"port\":\"80\",\"protocols\":[\"socks4\"]},"
                + "{\"ip\":\"11.12.13.14\",\"port\":\"3128\",\"protocols\":[\"http\",\"socks5\"],\"nested\":{\"a\":[1,2]}}"
                + "],\"total\":5,\"page\":1}";
        assertEquals(Arrays.asList(http("1.2.3.4", 8080), socks("5.6.7.8", 1080), http("11.12.13.14", 3128),
                socks("11.12.13.14", 3128)), ProxySources.parseGeonode(body));
    }

    @Test
    public void aGeonodeBodyCutAtTheSizeLimitKeepsTheRecordsBeforeTheCut() {
        String body = "{\"data\":[{\"ip\":\"1.2.3.4\",\"port\":\"8080\",\"protocols\":[\"http\"]},"
                + "{\"ip\":\"5.6.7.8\",\"port\":\"1080\",\"protocols\":[\"socks5\"]},{\"ip\":\"9.9.";
        assertEquals(Arrays.asList(http("1.2.3.4", 8080), socks("5.6.7.8", 1080)), ProxySources.parseGeonode(body));
        assertTrue(ProxySources.parseGeonode("<html>blocked</html>").isEmpty());
        assertTrue(ProxySources.parseGeonode("{\"message\":\"rate limited\"}").isEmpty());
    }

    @Test
    public void parseFollowsTheSourceFormat() {
        ProxySources.Source socksList = new ProxySources.Source("s", "https://x/s.txt", ProxySources.Format.IPPORT, Proxy.Type.SOCKS);
        ProxySources.Source mixed = new ProxySources.Source("m", "https://x/m.txt", ProxySources.Format.PROTOIPPORT, null);
        ProxySources.Source untyped = new ProxySources.Source("u", "https://x/u.txt", ProxySources.Format.IPPORT, null);
        assertEquals(Collections.singletonList(socks("1.2.3.4", 1080)), ProxySources.parse(socksList, "1.2.3.4:1080"));
        assertEquals(Collections.singletonList(http("1.2.3.4", 1080)), ProxySources.parse(mixed, "http://1.2.3.4:1080"));
        assertTrue(ProxySources.parse(untyped, "1.2.3.4:1080").isEmpty());
        assertTrue(ProxySources.parse(socksList, null).isEmpty());
        List<String> names = new ArrayList<>();
        for (ProxySources.Source source : ProxySources.ALL) {
            names.add(source.name);
        }
        assertEquals(Arrays.asList("proxyscrape", "zloi-socks5", "zloi-http", "monosans-socks5", "monosans-http",
                "elliottophellia-http", "elliottophellia-socks5", "aliilapro-socks5", "themiralay-http"), names);
        for (ProxySources.Source source : ProxySources.ALL) {
            assertTrue(source.name, source.url.startsWith("https://"));
            assertTrue(source.name, source.format != ProxySources.Format.IPPORT || source.type != null);
        }
    }

    @Test
    public void interleaveTakesTheListsRoundRobinWithoutDuplicates() {
        List<ProxyCandidate> huge = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            huge.add(http("1.1." + (i / 250) + "." + (i % 250), 80));
        }
        List<ProxyCandidate> small = Arrays.asList(socks("2.2.2.2", 1080), socks("2.2.2.3", 1080));
        List<ProxyCandidate> other = Arrays.asList(http("3.3.3.3", 80), http("1.1.0.1", 80));
        List<ProxyCandidate> out = ProxySources.interleave(Collections.emptyList(),
                Arrays.asList(huge, small, other), 400, new Random(7));

        assertEquals("one copy of 1.1.0.1:80 although two lists carry it", 203, out.size());
        assertEquals(out.size(), new HashSet<>(out).size());
        assertTrue("the small lists are not crowded out", out.subList(0, 6).containsAll(small));
        assertTrue(out.subList(0, 6).contains(http("3.3.3.3", 80)));
    }

    @Test
    public void interleavePutsTheFirstOnesFirstAndStopsAtTheCap() {
        List<ProxyCandidate> first = Arrays.asList(http("4.4.4.4", 80), socks("4.4.4.5", 1080));
        List<ProxyCandidate> list = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            list.add(http("5.5.5." + i, 80));
        }
        list.add(http("4.4.4.4", 80));
        List<ProxyCandidate> out = ProxySources.interleave(first, Collections.singletonList(list), 10, new Random(1));
        assertEquals(10, out.size());
        assertEquals(first, out.subList(0, 2));
        assertEquals(10, new HashSet<>(out).size());
        assertEquals(1, ProxySources.interleave(first, Collections.singletonList(list), 1, new Random(1)).size());
    }

    @Test
    public void onlyPublicIpv4AddressesAreCandidates() {
        assertTrue(ProxySources.isPublicIpv4("1.2.3.4"));
        assertTrue(ProxySources.isPublicIpv4("172.32.0.1"));
        assertTrue(ProxySources.isPublicIpv4("100.128.0.1"));
        assertTrue(ProxySources.isPublicIpv4("198.17.255.255"));
        assertTrue(ProxySources.isPublicIpv4("198.20.0.1"));
        assertTrue(ProxySources.isPublicIpv4("192.0.1.1"));
        assertTrue(ProxySources.isPublicIpv4("223.255.255.255"));
        for (String host : new String[]{"0.0.0.0", "192.0.0.8", "192.0.2.1", "198.18.0.1", "198.19.255.255",
                "198.51.100.7", "203.0.113.9", "240.0.0.1", "0.1.2.3", "10.1.2.3", "127.0.0.1", "169.254.1.1", "172.16.0.1",
                "172.31.255.255", "192.168.0.1", "100.64.0.1", "224.0.0.1", "255.255.255.255", "1.2.3",
                "1.2.3.4.5", "1.2.3.256", "a.b.c.d", "::1", "", "1..2.3"}) {
            assertFalse(host, ProxySources.isPublicIpv4(host));
        }
    }
}
