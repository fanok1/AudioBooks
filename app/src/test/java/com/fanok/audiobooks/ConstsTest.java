package com.fanok.audiobooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.view.KeyEvent;
import com.fanok.audiobooks.presenter.BookPresenter;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.Test;

public class ConstsTest {

    @Test
    public void socketFactoryIsThePlatformDefault() {
        assertEquals(SSLSocketFactory.getDefault().getClass(), Consts.socketFactory().getClass());
    }

    /** SEC-01: a server with an untrusted (self-signed) certificate must fail the handshake. */
    @Test(timeout = 20000)
    public void socketFactoryRejectsSelfSignedServer() throws Exception {
        HeldCertificate selfSigned = new HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build();
        HandshakeCertificates server = new HandshakeCertificates.Builder()
                .heldCertificate(selfSigned)
                .build();
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (SSLServerSocket serverSocket = (SSLServerSocket) server.sslContext()
                .getServerSocketFactory().createServerSocket(0, 1, loopback)) {
            Thread acceptor = new Thread(() -> {
                try (Socket accepted = serverSocket.accept()) {
                    ((SSLSocket) accepted).startHandshake();
                } catch (Exception ignored) {
                    // the client aborts the handshake
                }
            });
            acceptor.start();
            try (SSLSocket client = (SSLSocket) Consts.socketFactory()
                    .createSocket(loopback, serverSocket.getLocalPort())) {
                client.startHandshake();
                fail("handshake with a self-signed certificate succeeded");
            } catch (SSLHandshakeException expected) {
                // trust is checked
            }
            acceptor.join(5000);
        }
    }

    /** SEC-01 regression guard: no trust-all manager or accept-all verifier comes back. */
    @Test
    public void noCustomTrustManagerOrHostnameVerifierInConstsOrApp() {
        for (Class<?> owner : new Class<?>[]{Consts.class, App.class}) {
            for (Class<?> nested : owner.getDeclaredClasses()) {
                assertNotTrustOverride(nested);
            }
            ClassLoader loader = owner.getClassLoader();
            for (int i = 1; ; i++) {
                Class<?> anonymous;
                try {
                    anonymous = Class.forName(owner.getName() + "$" + i, false, loader);
                } catch (ClassNotFoundException end) {
                    break;
                }
                assertNotTrustOverride(anonymous);
            }
        }
    }

    private static void assertNotTrustOverride(Class<?> c) {
        assertFalse(c.getName(), TrustManager.class.isAssignableFrom(c));
        assertFalse(c.getName(), HostnameVerifier.class.isAssignableFrom(c));
        assertFalse(c.getName(), SSLSocketFactory.class.isAssignableFrom(c));
    }

    /** PLAY-05: PLAY only plays and PAUSE only pauses (a PAUSE while paused stays paused); PLAY_PAUSE toggles. */
    @Test
    public void mediaKeysMapToTheirOwnCommands() {
        assertEquals(BookPresenter.Broadcast_RESUME, Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_MEDIA_PLAY));
        assertEquals(BookPresenter.Broadcast_PAUSE, Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_MEDIA_PAUSE));
        assertEquals(BookPresenter.Broadcast_PLAY, Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        assertNotEquals(BookPresenter.Broadcast_RESUME, BookPresenter.Broadcast_PLAY);
        assertNotEquals(BookPresenter.Broadcast_PAUSE, BookPresenter.Broadcast_PLAY);
        assertEquals(BookPresenter.Broadcast_PLAY_NEXT, Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertEquals(BookPresenter.Broadcast_PLAY_PREVIOUS,
                Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        assertNull(Consts.mediaKeyBroadcast(KeyEvent.KEYCODE_A));
    }
}
