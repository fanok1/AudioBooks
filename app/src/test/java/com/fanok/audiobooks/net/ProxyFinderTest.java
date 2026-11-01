package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.Url;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;

public class ProxyFinderTest {

    private static final String TARGET = "https://akniga.org/";
    private static final ProxySources.Source HTTP_LIST =
            new ProxySources.Source("http", "https://lists.example/http.txt", ProxySources.Format.IPPORT, Proxy.Type.HTTP);
    private static final ProxySources.Source MIXED_LIST =
            new ProxySources.Source("mixed", "https://lists.example/mixed.txt", ProxySources.Format.PROTOIPPORT, null);

    /** Lists by source name; a proxy works when its label has a latency here. */
    private static final class FakeNetwork implements ProxyFinder.Network {
        final Map<String, String> lists = new ConcurrentHashMap<>();
        final Map<String, Long> latency = new ConcurrentHashMap<>();
        final AtomicInteger fetches = new AtomicInteger();
        final AtomicInteger probes = new AtomicInteger();
        final List<String> targets = Collections.synchronizedList(new ArrayList<>());
        /** Probes still to fail, by proxy label (a transient failure). */
        final Map<String, Integer> failNext = new ConcurrentHashMap<>();
        /** "label target": the site refuses this proxy. */
        final Set<String> refused = ConcurrentHashMap.newKeySet();
        volatile Runnable onFetch = () -> { };
        volatile long probeDelayMs;

        @NonNull
        @Override
        public String fetch(@NonNull ProxySources.Source source) throws IOException {
            fetches.incrementAndGet();
            onFetch.run();
            String body = lists.get(source.name);
            if (body == null) {
                throw new IOException("unreachable");
            }
            return body;
        }

        @Override
        public long probe(@NonNull ProxyCandidate candidate, @NonNull String target, int timeoutMs) throws IOException {
            probes.incrementAndGet();
            targets.add(target);
            if (probeDelayMs > 0) {
                try {
                    Thread.sleep(probeDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
            Long ms = latency.get(candidate.label());
            if (ms == null) {
                throw new IOException("dead");
            }
            Integer failures = failNext.get(candidate.label());
            if (failures != null && failures > 0) {
                failNext.put(candidate.label(), failures - 1);
                throw new IOException("transient");
            }
            if (refused.contains(candidate.label() + " " + target)) {
                throw new IOException("HTTP 403");
            }
            return ms;
        }
    }

    private final FakePrefs prefs = new FakePrefs();
    private final FakeNetwork network = new FakeNetwork();
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final List<ProxyFinder.State> heard = new ArrayList<>();
    private final AtomicInteger switches = new AtomicInteger();

    private final AtomicReference<String> target = new AtomicReference<>(TARGET);

    private ProxyFinder finder() {
        return finder(Runnable::run);
    }

    private ProxyFinder finder(Executor worker) {
        ProxyFinder finder = new ProxyFinder(prefs, network, Runnable::run, worker, online::get, target::get,
                now::get, new Random(3), Arrays.asList(HTTP_LIST, MIXED_LIST), 4, switches::incrementAndGet);
        finder.retryDelayMs = 0;
        finder.addListener(heard::add);
        return finder;
    }

    /** Runs the queued worker tasks, including those they queue. */
    private static void drain(List<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove(0).run();
        }
    }

    private void lists(String http, String mixed) {
        network.lists.put(HTTP_LIST.name, http);
        network.lists.put(MIXED_LIST.name, mixed);
    }

    @Test
    public void aScanKeepsTheWorkingProxiesFastestFirst() {
        lists("1.1.1.1:80\n2.2.2.2:80\n3.3.3.3:80\n", "socks5://4.4.4.4:1080\nhttp://5.5.5.5:8080\nsocks4://6.6.6.6:1080\n");
        network.latency.put("2.2.2.2:80", 900L);
        network.latency.put("4.4.4.4:1080", 120L);
        network.latency.put("5.5.5.5:8080", 400L);
        ProxyFinder finder = finder();

        finder.rescan();

        ProxyFinder.State state = finder.state();
        assertEquals(ProxyFinder.Phase.READY, state.phase);
        assertEquals(5, state.total);
        assertEquals(5, state.tested);
        assertEquals(3, state.found);
        assertEquals(now.get(), state.lastScanAt);
        assertNotNull(state.current);
        assertEquals("4.4.4.4:1080", state.current.label());
        assertSame(Proxy.Type.SOCKS, state.current.type());
        assertEquals(120L, state.current.latencyMs());
        assertEquals(now.get(), state.current.checkedAt());
        assertEquals(state.current.toProxy(), finder.currentProxy());
        assertTrue(network.targets.stream().allMatch(TARGET::equals));
        assertEquals(ProxyFinder.Phase.SCANNING, heard.get(0).phase);
        assertEquals(ProxyFinder.Phase.READY, heard.get(heard.size() - 1).phase);

        ProxyFinder.Saved saved = ProxyFinder.Saved.fromJson(prefs.getString(ProxyFinder.PREF_LIST, null));
        assertEquals(Arrays.asList("4.4.4.4:1080", "5.5.5.5:8080", "2.2.2.2:80"), labels(saved.proxies));
        assertEquals(0, saved.index);
    }

    @Test
    public void aScanStopsOnceEnoughProxiesWork() {
        StringBuilder list = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            list.append("7.7.7.").append(i).append(":80\n");
            network.latency.put("7.7.7." + i + ":80", (long) i);
        }
        lists(list.toString(), "");
        network.probeDelayMs = 50;
        ProxyFinder finder = finder();

        finder.rescan();

        assertEquals(ProxyFinder.ENOUGH, finder.state().found);
        assertEquals(ProxyFinder.ENOUGH, ProxyFinder.Saved.fromJson(prefs.getString(ProxyFinder.PREF_LIST, null)).proxies.size());
        assertTrue("checks stop early", network.probes.get() < 40);
    }

    @Test
    public void bestKeepsTheFastestEight() {
        List<ProxyCandidate> working = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            working.add(new ProxyCandidate("8.8.1." + i, 80, Proxy.Type.HTTP).checked(1000 - i * 10L, 1));
        }
        List<ProxyCandidate> best = ProxyFinder.best(working, ProxyFinder.KEEP);
        assertEquals(8, best.size());
        assertEquals("8.8.1.12:80", best.get(0).label());
        assertEquals("8.8.1.5:80", best.get(7).label());
        for (int i = 1; i < best.size(); i++) {
            assertTrue(best.get(i - 1).latencyMs() <= best.get(i).latencyMs());
        }
        assertTrue(ProxyFinder.best(Collections.emptyList(), 8).isEmpty());
    }

    @Test
    public void aConfirmedFailureMovesToTheNextProxy() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        int scans = network.fetches.get();
        InetSocketAddress first = (InetSocketAddress) finder.currentProxy().address();

        network.latency.remove("1.1.1.1:80");
        int probes = network.probes.get();
        finder.reportFailure(first);
        assertEquals("the probe and its retry", probes + 2, network.probes.get());
        finder.reportFailure(first);
        finder.reportFailure(InetSocketAddress.createUnresolved("9.9.9.9", 80));
        assertEquals("a proxy no longer in use is not probed", probes + 2, network.probes.get());

        assertEquals("2.2.2.2:80", finder.state().current.label());
        assertEquals(ProxyFinder.Phase.READY, finder.state().phase);
        assertEquals(1, ProxyFinder.Saved.fromJson(prefs.getString(ProxyFinder.PREF_LIST, null)).index);

        // the list is exhausted, but the last search is less than ten minutes old: no automatic search yet
        network.latency.remove("2.2.2.2:80");
        finder.reportFailure(finder.currentProxy().address());
        assertNull(finder.currentProxy());
        assertEquals(ProxyFinder.Phase.FAILED, finder.state().phase);
        assertEquals(scans, network.fetches.get());
        assertTrue(finder.proxiesForRequest().isEmpty());
        assertEquals(scans, network.fetches.get());

        // once it is due, the next request starts one
        now.addAndGet(ProxyFinder.AUTO_SCAN_INTERVAL_MS);
        network.latency.put("2.2.2.2:80", 200L);
        assertTrue(finder.proxiesForRequest().isEmpty());
        assertTrue(network.fetches.get() > scans);
        assertEquals("2.2.2.2:80", finder.state().current.label());
    }

    @Test
    public void failuresWhileOfflineAreNotTheProxys() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        network.latency.remove("1.1.1.1:80");
        int probes = network.probes.get();
        online.set(false);
        finder.reportFailure(finder.currentProxy().address(), true);
        finder.reportFailure(finder.currentProxy().address());
        finder.reportSuspect();
        assertEquals("1.1.1.1:80", finder.state().current.label());
        assertEquals(probes, network.probes.get());
    }

    @Test
    public void aReportQueuesOneProbeAndAPassingProbeKeepsTheProxy() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        List<Runnable> queue = new ArrayList<>();
        ProxyFinder finder = finder(queue::add);
        finder.rescan();
        drain(queue);
        int probes = network.probes.get();
        InetSocketAddress first = (InetSocketAddress) finder.currentProxy().address();

        // a burst of reports (a cancelled call, the event listener, a refusal) queues one probe
        finder.reportFailure(first);
        finder.reportFailure(first, true);
        finder.reportSuspect();
        assertEquals(1, queue.size());
        assertEquals("nothing moves before the probe", "1.1.1.1:80", finder.state().current.label());

        network.latency.put("1.1.1.1:80", 80L);
        drain(queue);
        assertEquals(probes + 1, network.probes.get());
        assertEquals(TARGET, network.targets.get(network.targets.size() - 1));
        assertEquals("1.1.1.1:80", finder.state().current.label());
        assertEquals("the probe refreshes the latency", 80L, finder.state().current.latencyMs());

        // a proxy that just passed is not probed again for a while, unless the failure is definite
        finder.reportFailure(first);
        finder.reportSuspect();
        assertTrue(queue.isEmpty());
        finder.reportFailure(first, true);
        assertEquals(1, queue.size());
        drain(queue);
        now.addAndGet(ProxyFinder.CONFIRM_QUIET_MS);
        finder.reportFailure(first);
        assertEquals(1, queue.size());
        drain(queue);
        assertEquals("1.1.1.1:80", finder.state().current.label());
        assertEquals(probes + 3, network.probes.get());
    }

    @Test
    public void aFailedProbeIsRetriedOnceBeforeMovingOn() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        int probes = network.probes.get();

        // a transient failure (Wi-Fi associating, a captive portal): the retry passes
        network.failNext.put("1.1.1.1:80", 1);
        finder.recheckCurrent();
        assertEquals(probes + 2, network.probes.get());
        assertEquals("1.1.1.1:80", finder.state().current.label());

        // both fail: the next proxy
        network.failNext.put("1.1.1.1:80", 2);
        now.addAndGet(ProxyFinder.CONFIRM_QUIET_MS);
        finder.reportSuspect();
        assertEquals(probes + 4, network.probes.get());
        assertEquals("2.2.2.2:80", finder.state().current.label());
    }

    @Test
    public void aSparesReportIsIgnored() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        network.latency.remove("1.1.1.1:80");
        int probes = network.probes.get();

        finder.reportFailure(finder.proxiesForRequest().get(1).address(), true);
        finder.reportFailure(finder.proxiesForRequest().get(1).address());

        assertEquals(probes, network.probes.get());
        assertEquals("1.1.1.1:80", finder.state().current.label());
    }

    @Test
    public void anotherSiteRechecksTheProxyInUseAgainstIt() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        String other = "https://other.example/";
        network.refused.add("1.1.1.1:80 " + other);

        // what AppProxy's listener does when "sorce_books" changes in the automatic mode
        target.set(other);
        finder.recheckCurrent();

        assertEquals(other, network.targets.get(network.targets.size() - 1));
        assertEquals("2.2.2.2:80", finder.state().current.label());
    }

    @Test
    public void aSearchThatLosesTheNetworkKeepsTheKnownList() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        long scannedAt = finder.state().lastScanAt;

        now.addAndGet(60_000L);
        network.onFetch = () -> {
            online.set(false);
            network.latency.clear();
        };
        finder.rescan();

        assertEquals(ProxyFinder.Phase.NO_NETWORK, finder.state().phase);
        assertEquals("1.1.1.1:80", finder.state().current.label());
        assertEquals(1, finder.state().spares);
        assertEquals(scannedAt, finder.state().lastScanAt);
        assertEquals(Arrays.asList("1.1.1.1:80", "2.2.2.2:80"),
                labels(ProxyFinder.Saved.fromJson(prefs.getString(ProxyFinder.PREF_LIST, null)).proxies));

        // the retry comes after the short no-network pause
        network.onFetch = () -> { };
        network.latency.put("1.1.1.1:80", 100L);
        online.set(true);
        int fetches = network.fetches.get();
        now.addAndGet(ProxyFinder.NO_NETWORK_RETRY_MS);
        finder.scanIfDue();
        assertTrue(network.fetches.get() > fetches);
        assertEquals(ProxyFinder.Phase.READY, finder.state().phase);
    }

    @Test
    public void noScanWithoutNetwork() {
        lists("1.1.1.1:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        online.set(false);
        ProxyFinder finder = finder();

        finder.ensureProxy();

        assertEquals(ProxyFinder.Phase.NO_NETWORK, finder.state().phase);
        assertEquals(0, network.fetches.get());
        assertEquals(0, network.probes.get());
        online.set(true);
        now.addAndGet(ProxyFinder.NO_NETWORK_RETRY_MS);
        finder.ensureProxy();
        assertEquals(ProxyFinder.Phase.READY, finder.state().phase);
    }

    @Test
    public void automaticScansAreRateLimitedButAManualOneAlwaysRuns() {
        lists("1.1.1.1:80\n", "");
        ProxyFinder finder = finder();

        finder.ensureProxy();
        assertEquals(ProxyFinder.Phase.FAILED, finder.state().phase);
        int fetches = network.fetches.get();
        finder.ensureProxy();
        now.addAndGet(ProxyFinder.AUTO_SCAN_INTERVAL_MS - 1);
        finder.ensureProxy();
        assertEquals(fetches, network.fetches.get());

        network.latency.put("1.1.1.1:80", 100L);
        finder.rescan();
        assertEquals(ProxyFinder.Phase.READY, finder.state().phase);
        assertTrue(network.fetches.get() > fetches);
    }

    @Test
    public void aRescanTriesTheRemainingVerifiedProxiesFirst() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        finder.rescan();
        network.lists.clear();

        finder.rescan();

        assertEquals("the lists are unreachable, the known proxies still work", "1.1.1.1:80", finder.state().current.label());
        assertEquals(2, finder.state().found);
    }

    @Test
    public void theListSurvivesARestartAndIsRecheckedOnStart() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        finder().rescan();

        ProxyFinder restarted = finder();
        assertEquals(ProxyFinder.Phase.READY, restarted.state().phase);
        assertEquals("1.1.1.1:80", restarted.state().current.label());

        network.latency.remove("1.1.1.1:80");
        int fetches = network.fetches.get();
        restarted.recheckCurrent();
        assertEquals("2.2.2.2:80", restarted.state().current.label());
        assertEquals(fetches, network.fetches.get());

        network.latency.put("2.2.2.2:80", 50L);
        restarted.recheckCurrent();
        assertEquals(50L, restarted.state().current.latencyMs());
    }

    @Test
    public void theRouteOffersTheProxyInUseAndTwoSpares() {
        lists("1.1.1.1:80\n2.2.2.2:80\n3.3.3.3:80\n", "socks5://4.4.4.4:1080\n");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        network.latency.put("3.3.3.3:80", 300L);
        network.latency.put("4.4.4.4:1080", 400L);
        ProxyFinder finder = finder();
        finder.rescan();

        assertEquals(Arrays.asList("1.1.1.1:80", "2.2.2.2:80", "3.3.3.3:80"), routeLabels(finder));
        assertEquals(3, finder.state().spares);
        assertSame(finder.currentProxy(), finder.proxiesForRequest().get(0));

        // a client that failed over to a spare reports the proxy in use first: that one counts; the spare that is not
        // in use yet does not
        network.latency.remove("1.1.1.1:80");
        network.latency.remove("2.2.2.2:80");
        finder.reportFailure(finder.proxiesForRequest().get(1).address());
        assertEquals("1.1.1.1:80", finder.state().current.label());
        finder.reportFailure(finder.proxiesForRequest().get(0).address());
        assertEquals(Arrays.asList("2.2.2.2:80", "3.3.3.3:80", "4.4.4.4:1080"), routeLabels(finder));
        assertEquals(2, finder.state().spares);

        network.latency.remove("3.3.3.3:80");
        finder.reportFailure(finder.currentProxy().address(), true);
        finder.reportFailure(finder.currentProxy().address(), true);
        assertEquals(Collections.singletonList("4.4.4.4:1080"), routeLabels(finder));
        assertEquals(0, finder.state().spares);
    }

    @Test
    public void pooledConnectionsAreDroppedWhenTheProxyInUseChanges() {
        lists("1.1.1.1:80\n2.2.2.2:80\n", "");
        network.latency.put("1.1.1.1:80", 100L);
        network.latency.put("2.2.2.2:80", 200L);
        ProxyFinder finder = finder();
        assertEquals(0, switches.get());

        finder.rescan();
        assertEquals("direct to the first proxy", 1, switches.get());
        finder.recheckCurrent();
        assertEquals("the same proxy, checked again", 1, switches.get());
        finder.rescan();
        assertEquals("a new search that keeps the proxy in use", 1, switches.get());
        network.latency.clear();
        finder.reportFailure(finder.currentProxy().address(), true);
        assertEquals(2, switches.get());
        finder.reportFailure(finder.currentProxy().address(), true);
        assertEquals("to the system's choice", 3, switches.get());
    }

    @Test
    public void aListThatFailsToDownloadDoesNotStopTheScan() {
        network.lists.put(MIXED_LIST.name, "http://5.5.5.5:8080\n");
        network.latency.put("5.5.5.5:8080", 300L);
        ProxyFinder finder = finder();

        finder.rescan();

        assertEquals(ProxyFinder.Phase.READY, finder.state().phase);
        assertEquals("5.5.5.5:8080", finder.state().current.label());
    }

    @Test
    public void onlyAnAnswerFrom200To399IsUsable() {
        for (int code : new int[]{200, 204, 206, 301, 302, 304, 399}) {
            assertTrue(String.valueOf(code), ProxyFinder.usable(code));
        }
        for (int code : new int[]{0, 101, 199, 400, 403, 407, 429, 451, 500, 502, 503}) {
            assertFalse(String.valueOf(code), ProxyFinder.usable(code));
        }
    }

    @Test
    public void theRealCheckPassesAPageAndRejectsABlockThroughAnHttpProxy() throws Exception {
        ProxyFinder.OkNetwork real = new ProxyFinder.OkNetwork();
        for (int status : new int[]{206, 302, 403, 429, 503}) {
            try (ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                String[] requestLine = new String[1];
                Thread thread = new Thread(() -> answerOnce(proxy, status, requestLine));
                thread.start();
                ProxyCandidate candidate = new ProxyCandidate("127.0.0.1", proxy.getLocalPort(), Proxy.Type.HTTP);
                boolean passed;
                try {
                    assertTrue(real.probe(candidate, "http://catalog.invalid/", 2_000) >= 1);
                    passed = true;
                } catch (IOException e) {
                    passed = false;
                }
                thread.join(5_000);
                assertEquals(String.valueOf(status), status < 400, passed);
                assertEquals("the proxy is asked for the site", "GET http://catalog.invalid/ HTTP/1.1", requestLine[0]);
            }
        }
    }

    @Test
    public void theRealCheckPostsToAudiobooAndGetsAnyOtherSite() throws Exception {
        ProxyFinder.OkNetwork real = new ProxyFinder.OkNetwork();
        String audioboo = HttpUrl.get(Url.SERVER_AUDIOBOO).newBuilder().scheme("http").build().toString();
        assertTrue(Url.isHost(audioboo, Url.SERVER_AUDIOBOO));
        for (String target : new String[]{audioboo, "http://catalog.invalid/"}) {
            try (ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                String[] requestLine = new String[1];
                Thread thread = new Thread(() -> answerOnce(proxy, 200, requestLine));
                thread.start();
                ProxyCandidate candidate = new ProxyCandidate("127.0.0.1", proxy.getLocalPort(), Proxy.Type.HTTP);
                assertTrue(real.probe(candidate, target, 2_000) >= 1);
                thread.join(5_000);
                String method = target.equals(audioboo) ? "POST" : "GET";
                assertEquals(method + " " + target + " HTTP/1.1", requestLine[0]);
            }
        }
    }

    /** An HTTP proxy that answers one request itself with {@code status}. */
    private static void answerOnce(ServerSocket server, int status, String[] requestLine) {
        try (Socket socket = server.accept()) {
            BufferedReader request = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            requestLine[0] = request.readLine();
            for (String line = request.readLine(); line != null && !line.isEmpty(); line = request.readLine()) {
                // headers are not needed
            }
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 " + status + " X\r\nLocation: http://catalog.invalid/next\r\n"
                    + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (IOException ignored) {
            // the test fails on its own assertions
        }
    }

    private static List<String> routeLabels(ProxyFinder finder) {
        List<String> labels = new ArrayList<>();
        for (Proxy proxy : finder.proxiesForRequest()) {
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            labels.add(address.getHostString() + ":" + address.getPort());
        }
        return labels;
    }

    @Test
    public void theSavedListRoundTrips() {
        List<ProxyCandidate> proxies = Arrays.asList(
                new ProxyCandidate("1.2.3.4", 8080, Proxy.Type.HTTP).checked(420, 1_700_000_000_000L),
                new ProxyCandidate("5.6.7.8", 1080, Proxy.Type.SOCKS).checked(950, 1_700_000_000_001L));
        ProxyFinder.Saved saved = ProxyFinder.Saved.fromJson(ProxyFinder.Saved.toJson(proxies, 1, 1_700_000_000_002L));
        assertEquals(proxies, saved.proxies);
        assertEquals(1, saved.index);
        assertEquals(1_700_000_000_002L, saved.scannedAt);
        assertEquals(420, saved.proxies.get(0).latencyMs());
        assertEquals(1_700_000_000_001L, saved.proxies.get(1).checkedAt());
        assertSame(Proxy.Type.SOCKS, saved.proxies.get(1).type());
    }

    @Test
    public void anUnreadableSavedListIsEmpty() {
        assertTrue(ProxyFinder.Saved.fromJson(null).proxies.isEmpty());
        assertTrue(ProxyFinder.Saved.fromJson("").proxies.isEmpty());
        assertTrue(ProxyFinder.Saved.fromJson("not json").proxies.isEmpty());
        assertTrue(ProxyFinder.Saved.fromJson("[1,2]").proxies.isEmpty());
        String tampered = "{\"current\":0,\"proxies\":[{\"host\":\"127.0.0.1\",\"port\":80,\"type\":\"http\"},"
                + "{\"host\":\"1.2.3.4\",\"port\":80,\"type\":\"socks4\"},{\"host\":\"1.2.3.4\",\"port\":80,\"type\":\"http\"}]}";
        assertEquals(Collections.singletonList("1.2.3.4:80"), labels(ProxyFinder.Saved.fromJson(tampered).proxies));
        prefs.edit().putInt(ProxyFinder.PREF_LIST, 3).apply();
        ProxyFinder finder = finder();
        assertEquals(ProxyFinder.Phase.IDLE, finder.state().phase);
        assertNull(finder.currentProxy());
    }

    @Test
    public void candidatesCompareByAddressAndType() {
        ProxyCandidate a = new ProxyCandidate("1.2.3.4", 80, Proxy.Type.HTTP);
        assertEquals(a, a.checked(5, 6));
        assertFalse(a.equals(new ProxyCandidate("1.2.3.4", 80, Proxy.Type.SOCKS)));
        assertTrue(a.hasAddress(InetSocketAddress.createUnresolved("1.2.3.4", 80)));
        assertTrue(a.hasAddress(new InetSocketAddress("1.2.3.4", 80)));
        assertFalse(a.hasAddress(InetSocketAddress.createUnresolved("1.2.3.4", 81)));
        assertFalse(a.hasAddress(null));
    }

    private static List<String> labels(List<ProxyCandidate> proxies) {
        List<String> labels = new ArrayList<>();
        for (ProxyCandidate proxy : proxies) {
            labels.add(proxy.label());
        }
        return labels;
    }
}
