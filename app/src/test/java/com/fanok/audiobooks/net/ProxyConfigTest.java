package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.Consts;

import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

/** "Свой прокси": the build's server until the user saves another; the login goes only to the proxy it belongs to. */
public class ProxyConfigTest {

    @After
    public void restoreTheBuildsServer() {
        AppProxy.setCustom(new FakePrefs(), null);
    }

    @Test
    public void nothingStoredIsTheBuildsServerWithItsOwnPassword() {
        ProxyConfig config = ProxyConfig.read(new FakePrefs());
        assertEquals(ProxyConfig.builtIn(), config);
        assertEquals(Consts.PROXY_HOST, config.host);
        assertEquals(Consts.PROXY_PORT, config.port);
        assertTrue(config.ownPassword);
        assertEquals(Consts.PROXY_PASSWORD, config.password);
        assertEquals(Proxy.Type.SOCKS, config.toProxy().type());
    }

    @Test
    public void theUsersServerIsStoredAndReadBack() {
        FakePrefs prefs = new FakePrefs();
        new ProxyConfig(ProxyConfig.TYPE_HTTP, " proxy.example ", 3128, "me", "secret", false).save(prefs);
        ProxyConfig back = ProxyConfig.read(prefs);
        assertEquals(ProxyConfig.TYPE_HTTP, back.type);
        assertEquals("proxy.example", back.host);
        assertEquals(3128, back.port);
        assertEquals("me", back.user);
        assertEquals("secret", back.password);
        assertFalse(back.ownPassword);
        assertEquals(Proxy.Type.HTTP, back.toProxy().type());
        assertEquals("proxy.example", ((InetSocketAddress) back.toProxy().address()).getHostString());
    }

    @Test
    public void theBuildsPasswordIsNeverStored() {
        FakePrefs prefs = new FakePrefs();
        new ProxyConfig(ProxyConfig.TYPE_SOCKS, "proxy.example", 1080, "me", "ignored", true).save(prefs);
        assertFalse(prefs.contains(ProxyConfig.PREF_PASSWORD));
        assertTrue(ProxyConfig.read(prefs).ownPassword);
        ProxyConfig.reset(prefs);
        assertEquals(ProxyConfig.builtIn(), ProxyConfig.read(prefs));
    }

    @Test
    public void wronglyTypedValuesAreTheBuilds() {
        FakePrefs prefs = new FakePrefs();
        prefs.edit().putString(ProxyConfig.PREF_PORT, "80").putInt(ProxyConfig.PREF_HOST, 5)
                .putString(ProxyConfig.PREF_TYPE, "https").apply();
        ProxyConfig back = ProxyConfig.read(prefs);
        assertEquals(Consts.PROXY_PORT, back.port);
        assertEquals(Consts.PROXY_HOST, back.host);
        assertEquals("an unknown type is SOCKS5", ProxyConfig.TYPE_SOCKS, back.type);
    }

    @Test
    public void theFieldToFixIsNamed() {
        assertEquals(ProxyConfig.Problem.NONE, ProxyConfig.check("10.0.0.1", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.NONE, ProxyConfig.check("proxy.example", 65535, "me", "secret"));
        assertEquals(ProxyConfig.Problem.HOST, ProxyConfig.check(" ", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.HOST, ProxyConfig.check("http://proxy.example", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.HOST, ProxyConfig.check("me@proxy.example", 1080, "", ""));
        assertEquals("a port of its own", ProxyConfig.Problem.HOST, ProxyConfig.check("proxy.example:3128", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.HOST, ProxyConfig.check("[2001:db8::1]:1080", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.NONE, ProxyConfig.check("2001:db8::1", 1080, "", ""));
        assertEquals(ProxyConfig.Problem.PORT, ProxyConfig.check("proxy.example", 0, "", ""));
        assertEquals(ProxyConfig.Problem.PORT, ProxyConfig.check("proxy.example", 70000, "", ""));
        assertEquals("a password needs its login", ProxyConfig.Problem.USER,
                ProxyConfig.check("proxy.example", 1080, " ", "secret"));
        assertEquals(0, ProxyConfig.port("12a"));
        assertEquals(8080, ProxyConfig.port(" 8080 "));
    }

    @Test
    public void theLoginGoesOnlyToItsOwnProxy() {
        AppProxy.setCustom(new FakePrefs(),
                new ProxyConfig(ProxyConfig.TYPE_SOCKS, "Proxy.Example", 1080, "me", "secret", false));
        PasswordAuthentication login = AppProxy.loginFor("proxy.example", 1080);
        assertEquals("me", login.getUserName());
        assertEquals("secret", new String(login.getPassword()));
        assertNull("another port", AppProxy.loginFor("proxy.example", 1081));
        assertNull("a site", AppProxy.loginFor("akniga.org", 443));
        assertNull(AppProxy.loginFor(null, 1080));
        AppProxy.setCustom(new FakePrefs(),
                new ProxyConfig(ProxyConfig.TYPE_SOCKS, "proxy.example", 1080, "", "", false));
        assertNull("no login set", AppProxy.loginFor("proxy.example", 1080));
    }
}
