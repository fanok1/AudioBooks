package com.fanok.audiobooks.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActivityCookiesTest {

    @Test
    public void sessionCookieIsMatchedByExactNameAndSplitOnTheFirstEquals() {
        assertEquals("abc", ActivityCookies.sessionCookie("x=1; PHPSESSID=abc; y=2"));
        assertEquals("a=b", ActivityCookies.sessionCookie("PHPSESSID=a=b"));
        assertEquals("", ActivityCookies.sessionCookie("OLD_PHPSESSID=abc; PHPSESSIDX=1"));
        assertEquals("", ActivityCookies.sessionCookie("PHPSESSID="));
        assertEquals("", ActivityCookies.sessionCookie("PHPSESSID"));
        assertEquals("", ActivityCookies.sessionCookie(null));
    }
}
