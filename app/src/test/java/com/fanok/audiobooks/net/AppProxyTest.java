package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.search.Provider;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppProxyTest {

    private static final Proxy SOCKS = new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.example", 1080));
    private static final Proxy AUTO = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("8.8.4.4", 3128));
    private static final Proxy SPARE1 = new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("8.8.4.5", 1080));
    private static final Proxy SPARE2 = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("8.8.4.6", 8080));

    private final List<SocketAddress> systemFailures = new ArrayList<>();
    private final ProxySelector fallback = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            systemFailures.add(sa);
        }
    };

    @After
    public void restoreAuthenticator() {
        Authenticator.setDefault(null);
    }

    @Test
    public void webTrafficFollowsTheRoute() {
        List<List<Proxy>> route = new ArrayList<>(Collections.singletonList(Collections.singletonList(SOCKS)));
        AppProxy.Selector selector = new AppProxy.Selector(fallback, () -> route.get(0), (sa, e) -> { });
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("https://akniga.org/x")));
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("http://cdn.example/a.mp3")));
        route.set(0, Collections.emptyList());
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("https://akniga.org/x")));
        route.set(0, null);
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("https://akniga.org/x")));
        route.set(0, Arrays.asList(AUTO, SPARE1, SPARE2));
        assertEquals("the proxy in use, then the spares, in order", Arrays.asList(AUTO, SPARE1, SPARE2),
                selector.select(URI.create("https://akniga.org/x")));
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("ftp://files.example/")));
    }

    @Test
    public void aDirectSourceBypassesTheProxyAndAnyOtherHostFollowsIt() {
        AppProxy.Selector selector = new AppProxy.Selector(fallback, () -> Collections.singletonList(SOCKS),
                host -> !"akniga.org".equals(host), (sa, e) -> { });
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("https://akniga.org/x")));
        assertEquals("www. is the same site", Collections.singletonList(Proxy.NO_PROXY),
                selector.select(URI.create("https://WWW.akniga.org/x")));
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("https://uknig.com/x")));
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("https://cdn.example/a.mp3")));
    }

    @Test
    public void onlyTheSourcesSetDirectGoDirect() {
        Set<String> direct = new HashSet<>(Collections.singletonList(Provider.AKNIGA.prefValue));
        assertFalse(AppProxy.isProxied(Provider.AKNIGA, direct));
        assertTrue(AppProxy.isProxied(Provider.UKNIG, direct));
        assertTrue("an unknown host (a shared CDN) follows the mode", AppProxy.isProxied(null, direct));
        assertTrue("nothing stored: every source takes the proxy",
                AppProxy.isProxied(Provider.AKNIGA, Collections.<String>emptySet()));
    }

    /** A direct source's audio leaves the app's selector for the device's own route, so a shared CDN goes direct too. */
    @Test
    public void aDirectSourcesMediaTakeTheDeviceRouteOnEveryHost() {
        okhttp3.OkHttpClient shared = new okhttp3.OkHttpClient.Builder().proxySelector(new ProxySelector() {
            @Override
            public java.util.List<Proxy> select(URI uri) {
                return Collections.singletonList(SOCKS);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            }
        }).build();
        ProxySelector device = new ProxySelector() {
            @Override
            public java.util.List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            }
        };
        assertSame(device, AppProxy.onDeviceRoute(shared, device).proxySelector());
        assertEquals("before the selector is installed", Proxy.NO_PROXY, AppProxy.onDeviceRoute(shared, null).proxy());
        assertSame("a source on the proxy route keeps the shared client", shared,
                AppProxy.mediaClient(Provider.UKNIG, shared));
        assertSame(shared, AppProxy.mediaClient(null, shared));
    }

    @Test
    public void theDirectSetIsReadSafely() {
        FakePrefs prefs = new FakePrefs();
        assertTrue(AppProxy.readDirect(prefs).isEmpty());
        prefs.edit().putStringSet(AppProxy.PREF_DIRECT, new HashSet<>(Arrays.asList("abook", "uknig"))).apply();
        assertEquals(new HashSet<>(Arrays.asList("abook", "uknig")), AppProxy.readDirect(prefs));
        prefs.edit().putString(AppProxy.PREF_DIRECT, "garbled").apply();
        assertTrue(AppProxy.readDirect(prefs).isEmpty());
    }

    @Test
    public void theAutomaticProxyIsCheckedAgainstASourceThatTakesIt() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString("sorce_books", Provider.UKNIG.prefValue).apply();
        assertEquals(Provider.UKNIG.server + "/", ProxyFinder.targetOf(prefs));
        prefs.edit().putStringSet(AppProxy.PREF_DIRECT,
                new HashSet<>(Arrays.asList(Provider.UKNIG.prefValue, Provider.AKNIGA.prefValue))).apply();
        assertEquals("the selected source goes direct: the first proxied one",
                Provider.values()[1].server + "/", ProxyFinder.targetOf(prefs));
    }

    @Test
    public void eachModeRoutesItsOwnWay() {
        List<Proxy> builtin = Collections.singletonList(SOCKS);
        List<Proxy> auto = Arrays.asList(AUTO, SPARE1);
        assertTrue(AppProxy.route(AppProxy.MODE_OFF, true, builtin, () -> auto).isEmpty());
        assertSame(builtin, AppProxy.route(AppProxy.MODE_BUILTIN, true, builtin, () -> auto));
        assertTrue("a build without the built-in proxy",
                AppProxy.route(AppProxy.MODE_BUILTIN, false, builtin, () -> auto).isEmpty());
        assertSame(auto, AppProxy.route(AppProxy.MODE_AUTO, true, builtin, () -> auto));
        assertTrue("no verified proxy yet: the system's choice",
                AppProxy.route(AppProxy.MODE_AUTO, true, builtin, Collections::emptyList).isEmpty());
        assertTrue(AppProxy.route(AppProxy.MODE_AUTO, true, builtin, () -> null).isEmpty());
    }

    @Test
    public void aFailedConnectionIsReportedAndNeverThrown() {
        List<SocketAddress> reported = new ArrayList<>();
        AppProxy.Selector selector = new AppProxy.Selector(fallback, () -> Collections.singletonList(AUTO), (sa, e) -> {
            reported.add(sa);
            throw new IllegalStateException("listener bug");
        });
        selector.connectFailed(URI.create("https://akniga.org/"), AUTO.address(), new IOException("refused"));
        assertEquals(Collections.singletonList(AUTO.address()), reported);
        assertEquals(Collections.singletonList(AUTO.address()), systemFailures);
    }

    @Test
    public void theOldSwitchOnBecomesTheBuiltInModeOnce() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putBoolean(AppProxy.PREF, true).apply();
        assertEquals(AppProxy.MODE_BUILTIN, AppProxy.readMode(prefs));
        assertFalse(prefs.contains(AppProxy.PREF));
        assertEquals(AppProxy.MODE_BUILTIN, prefs.getString(AppProxy.PREF_MODE, null));
        assertEquals(AppProxy.MODE_BUILTIN, AppProxy.readMode(prefs));
    }

    @Test
    public void theOldSwitchOffIsDroppedAndMeansOff() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putBoolean(AppProxy.PREF, false).apply();
        assertEquals(AppProxy.MODE_OFF, AppProxy.readMode(prefs));
        assertFalse(prefs.contains(AppProxy.PREF));
    }

    @Test
    public void aWronglyTypedOldSwitchMeansOff() {
        // DATA-12: an older version stored the switch as a string
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString(AppProxy.PREF, "true").apply();
        assertEquals(AppProxy.MODE_OFF, AppProxy.readMode(prefs));
        assertFalse(prefs.contains(AppProxy.PREF));
    }

    @Test
    public void aChosenModeIsNotOverwrittenByTheOldSwitch() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putBoolean(AppProxy.PREF, true).putString(AppProxy.PREF_MODE, AppProxy.MODE_AUTO).apply();
        assertEquals(AppProxy.MODE_AUTO, AppProxy.readMode(prefs));
        assertFalse(prefs.contains(AppProxy.PREF));
    }

    @Test
    public void anUnknownOrWronglyTypedModeIsOff() {
        FakePrefs prefs = new FakePrefs();
        assertEquals(AppProxy.MODE_OFF, AppProxy.readMode(prefs));
        prefs.edit().putString(AppProxy.PREF_MODE, "turbo").apply();
        assertEquals(AppProxy.MODE_OFF, AppProxy.readMode(prefs));
        prefs.edit().putBoolean(AppProxy.PREF_MODE, true).apply();
        assertEquals(AppProxy.MODE_OFF, AppProxy.readMode(prefs));
        assertFalse(prefs.contains(AppProxy.PREF_MODE));
        prefs.edit().putString(AppProxy.PREF_MODE, AppProxy.MODE_AUTO).apply();
        assertEquals(AppProxy.MODE_AUTO, AppProxy.readMode(prefs));
    }

    @Test
    public void credentialsGoOnlyToTheProxyItself() {
        Authenticator.setDefault(new AppProxy.ProxyOnlyAuthenticator("proxy.example", 1080, "user", "secret"));
        PasswordAuthentication own = Authenticator.requestPasswordAuthentication(
                "proxy.example", null, 1080, "SOCKS5", "SOCKS authentication", null);
        assertEquals("user", own.getUserName());
        assertEquals("secret", new String(own.getPassword()));
        assertNull(Authenticator.requestPasswordAuthentication("akniga.org", null, 443, "https", "Login", "Basic"));
        assertNull(Authenticator.requestPasswordAuthentication("proxy.example", null, 8080, "SOCKS5", "SOCKS authentication", null));
        // a public proxy of the automatic mode asking for SOCKS authentication gets nothing
        assertNull(Authenticator.requestPasswordAuthentication("8.8.4.4", null, 3128, "SOCKS5", "SOCKS authentication", null));
    }

    @Test
    public void anUnconfiguredBuildNeverAnswersAChallenge() {
        Authenticator.setDefault(new AppProxy.ProxyOnlyAuthenticator("", 0, "", ""));
        assertNull(Authenticator.requestPasswordAuthentication("", null, 0, "SOCKS5", "SOCKS authentication", null));
        assertSame(Proxy.Type.SOCKS, AppProxy.proxy().type());
        assertTrue(AppProxy.normalize(null).equals(AppProxy.MODE_OFF));
    }

    /** The sources dialog stores one site's route, or every site's in one write. */
    @Test
    public void theRouteOfASiteOrOfEverySiteIsStored() {
        FakePrefs prefs = new FakePrefs();
        AppProxy.setDirect(prefs, Provider.AKNIGA, true);
        AppProxy.setDirect(prefs, Provider.UKNIG, true);
        AppProxy.setDirect(prefs, Provider.UKNIG, false);
        assertEquals(Collections.singleton(Provider.AKNIGA.prefValue), AppProxy.readDirect(prefs));
        AppProxy.setAllDirect(prefs, true);
        assertEquals(Provider.values().length, AppProxy.readDirect(prefs).size());
        assertFalse(AppProxy.isProxied(Provider.IZIBUK, AppProxy.readDirect(prefs)));
        AppProxy.setAllDirect(prefs, false);
        assertTrue("all through the proxy: none direct", AppProxy.readDirect(prefs).isEmpty());
    }
}
