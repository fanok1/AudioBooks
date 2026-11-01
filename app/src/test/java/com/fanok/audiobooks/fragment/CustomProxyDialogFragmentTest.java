package com.fanok.audiobooks.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

/** How the "Свой прокси" check reads a failure: a wrapped timeout, a refused login. */
public class CustomProxyDialogFragmentTest {

    @Test
    public void aWrappedTimeoutIsFoundInTheCauses() {
        IOException wrapped = new IOException("SOCKS connect", new SocketTimeoutException("Read timed out"));
        assertTrue(CustomProxyDialogFragment.caused(wrapped, SocketTimeoutException.class));
        assertFalse(CustomProxyDialogFragment.caused(new IOException("x"), SocketTimeoutException.class));
        assertEquals("socks connect\nread timed out\n", CustomProxyDialogFragment.messages(wrapped));
        assertEquals("", CustomProxyDialogFragment.messages(null));
    }

    @Test
    public void aRefusedLoginIsRecognized() {
        assertTrue(CustomProxyDialogFragment.loginRefused(new IOException("SOCKS : authentication failed")));
        assertTrue(CustomProxyDialogFragment.loginRefused(new IOException("Failed to authenticate with proxy")));
        assertFalse(CustomProxyDialogFragment.loginRefused(new IOException("Connection reset")));
        assertFalse(CustomProxyDialogFragment.loginRefused(null));
    }
}
