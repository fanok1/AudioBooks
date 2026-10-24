package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class AppProxyTest {

    private static final Proxy SOCKS = new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.example", 1080));
    @After
    public void restoreAuthenticator() {
        Authenticator.setDefault(null);
    }

    @Test
    public void webTrafficFollowsTheSwitch() {
        boolean[] on = {true};
        ProxySelector fallback = new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            }
        };
        AppProxy.Selector selector = new AppProxy.Selector(fallback, () -> on[0], SOCKS);
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("https://akniga.org/x")));
        assertEquals(Collections.singletonList(SOCKS), selector.select(URI.create("http://cdn.example/a.mp3")));
        on[0] = false;
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("https://akniga.org/x")));
        on[0] = true;
        assertEquals(Collections.singletonList(Proxy.NO_PROXY), selector.select(URI.create("ftp://files.example/")));
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
    }

    @Test
    public void anUnconfiguredBuildNeverAnswersAChallenge() {
        Authenticator.setDefault(new AppProxy.ProxyOnlyAuthenticator("", 0, "", ""));
        assertNull(Authenticator.requestPasswordAuthentication("", null, 0, "SOCKS5", "SOCKS authentication", null));
        assertSame(Proxy.Type.SOCKS, AppProxy.proxy().type());
    }
}
