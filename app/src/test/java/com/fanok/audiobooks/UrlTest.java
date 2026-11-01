package com.fanok.audiobooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.search.Provider;

import org.junit.Test;

public class UrlTest {

    @Test
    public void hostOfParsesAndNormalises() {
        assertEquals("baza-knig.top", Url.hostOf("https://WWW.baza-knig.top/page/2/?q=%D0%B0"));
        assertEquals("", Url.hostOf("not a url"));
        assertEquals("", Url.hostOf(null));
        assertEquals("", Url.hostOf("/relative/path"));
    }

    @Test
    public void isHostComparesParsedHostsNotStrings() {
        assertTrue(Url.isHost("https://baza-knig.top/x/y", Url.SERVER_BAZA_KNIG));
        assertTrue(Url.isHost("https://www.knigoblud.club/", Url.SERVER_KNIGOBLUD));
        assertFalse(Url.isHost("https://baza-knig.top.evil.com/", Url.SERVER_BAZA_KNIG));
        assertFalse(Url.isHost("file:///sdcard/baza-knig.top", Url.SERVER_BAZA_KNIG));
    }

    @Test
    public void samePageIgnoresSchemeWwwTrailingSlashQueryAndFormerHosts() {
        assertTrue(Url.samePage("https://knigavuhe.org/book/devjatiehtazhka-2/",
                "http://www.knigavuhe.org/book/devjatiehtazhka-2"));
        assertTrue(Url.samePage("https://m.knigavuhe.org/book/x/?ref=1#comments", "https://knigavuhe.org/book/x/"));
        assertTrue(Url.samePage("https://izibuk.ru/book123", "https://izib.uk/book123"));
        assertTrue(Url.samePage("https://akniga.org/%D0%BA%D0%BD%D0%B8%D0%B3%D0%B0", "https://akniga.org/книга/"));
        assertFalse(Url.samePage("https://knigavuhe.org/book/x/", "https://knigavuhe.org/book/x-2/"));
        assertFalse(Url.samePage("https://knigavuhe.org/book/x/", "https://akniga.org/book/x/"));
        assertFalse(Url.samePage("", "https://knigavuhe.org/book/x/"));
        assertFalse(Url.samePage(null, null));
    }

    @Test
    public void hostOfToleratesWhatBrowsersEncode() {
        // akniga links authors and readers with raw spaces; the filters on the book screen route by host
        assertEquals("akniga.org", Url.hostOf("https://akniga.org/author/Кинг Стивен/page1/"));
        assertEquals("akniga.org", Url.hostOf("https://akniga.org/performer/Панков Роман/page<page>/"));
        assertEquals("akniga.org", Url.hostOf("https://akniga.org/author/Кинг Стивен/"));
        assertEquals("akniga.org", Url.hostOf("https://akniga.org/x?q=a|b"));
        assertEquals("akniga.org", Url.hostOf("https://akniga.org/%zz"));
        assertTrue(Url.isHost("https://akniga.org/author/Кинг Стивен/page1/", Url.SERVER_AKNIGA));
        assertEquals(Provider.AKNIGA, Provider.ofUrl("https://akniga.org/author/Кинг Стивен/page1/"));
    }

    @Test
    public void hostOfStaysStrictOnStructure() {
        assertEquals("", Url.hostOf("https://baza-knig.top\\@evil.com/"));
        assertEquals("", Url.hostOf("https://evil.com\\@baza-knig.top/"));
        assertFalse(Url.isHost("https://baza-knig.top\\@evil.com/", Url.SERVER_BAZA_KNIG));
        // a space in the authority splits it as java.net.URL does: the host is after the '@'
        assertEquals("evil.com", Url.hostOf("https://baza-knig.top @evil.com/"));
        assertFalse(Url.isHost("https://baza-knig.top.evil.com/a b", Url.SERVER_BAZA_KNIG));
    }

    @Test
    public void canonicalKeepsTheRawStringAroundTheAuthority() {
        assertEquals("https://baza-knig.top/xfsearch/autor/Иван Петров/",
                Url.canonical("http://baza-knig.ink/xfsearch/autor/Иван Петров/"));
        assertEquals("https://akniga.org/author/Кинг Стивен/page<page>/",
                Url.canonical("https://akniga.org/author/Кинг Стивен/page<page>/"));
        assertEquals("https://a$1@baza-knig.top/x y", Url.canonical("https://a$1@baza-knig.ink/x y"));
        assertEquals("https://www.izib.uk/b/2?q=a b#f|g", Url.canonical("http://www.izibuk.ru/b/2?q=a b#f|g"));
        // an authority that itself needed encoding is left alone
        assertEquals("http://u v@baza-knig.ink/x", Url.canonical("http://u v@baza-knig.ink/x"));
    }

    @Test
    public void audiobooListingPagesFollowTheSiteScheme() {
        assertEquals("https://audioboo.org/", Url.audiobooListingPage(Url.INDEX_AUDIOBOO, 1));
        assertEquals("https://audioboo.org/page/2/", Url.audiobooListingPage(Url.INDEX_AUDIOBOO, 2));
        assertEquals("https://audioboo.org/mistic/", Url.audiobooListingPage("https://audioboo.org/mistic/", 1));
        assertEquals("https://audioboo.org/mistic/page/3/", Url.audiobooListingPage("https://audioboo.org/mistic/page/", 3));
        assertEquals("https://audioboo.org/didiktiva/page/2/",
                Url.audiobooListingPage("https://audioboo.org/didiktiva/page/5/", 2));
        String author = "https://audioboo.org/xfsearch/avtora/%D0%9A%D0%B8%D0%BD%D0%B3%20%D0%A1/";
        assertEquals(author, Url.audiobooListingPage(author, 1));
        assertEquals(author + "page/2/", Url.audiobooListingPage(author, 2));
        assertEquals("https://audioboo.org/tags/x/", Url.audiobooListingPage("https://audioboo.org/tags/x", 1));
        // a query or fragment stays after the path
        assertEquals("https://audioboo.org/mistic/page/2/?sort=rating",
                Url.audiobooListingPage("https://audioboo.org/mistic/?sort=rating", 2));
        assertEquals("https://audioboo.org/mistic/page/2/#books",
                Url.audiobooListingPage("https://audioboo.org/mistic/#books", 2));
    }

    @Test
    public void abmp3ListingPagesAreOnlyAQueryParameter() {
        // the site answers "?page=2/" with HTTP 500, and an author's "?page=1/" too
        assertEquals("https://audiobook-mp3.com/", Url.abmp3ListingPage(Url.INDEX_ABMP3, 1));
        assertEquals("https://audiobook-mp3.com/?page=2", Url.abmp3ListingPage(Url.INDEX_ABMP3, 2));
        assertEquals("https://audiobook-mp3.com/avtor-573-x", Url.abmp3ListingPage(
                Url.SERVER_ABMP3 + "/avtor-573-x?page=", 1));
        assertEquals("https://audiobook-mp3.com/genre-20-uzhasy-mistika?page=3", Url.abmp3ListingPage(
                Url.SERVER_ABMP3 + "/genre-20-uzhasy-mistika?page=", 3));
        assertEquals("https://audiobook-mp3.com/top?page=2", Url.abmp3ListingPage(Url.ABMP3_TOP, 2));
        assertEquals("https://audiobook-mp3.com/authors?page=4", Url.abmp3ListingPage(Url.AUTHORS_ABMP3, 4));
        // a stale page value is replaced, other parameters stay
        assertEquals("https://audiobook-mp3.com/x?sort=new&page=5",
                Url.abmp3ListingPage(Url.SERVER_ABMP3 + "/x?page=2&sort=new", 5));
    }

    @Test
    public void withoutFirstPageDropsOnlyPageOne() {
        assertEquals("https://audioboo.org/", Url.withoutFirstPage("https://audioboo.org/page/1/"));
        assertEquals("https://audioboo.org/", Url.withoutFirstPage("https://audioboo.org/page/1"));
        assertEquals("https://audioboo.org/didiktiva/", Url.withoutFirstPage("https://audioboo.org/didiktiva/page/1/"));
        assertEquals("https://audioboo.org/?x=1", Url.withoutFirstPage("https://audioboo.org/page/1/?x=1"));
        assertEquals("https://audioboo.org/page/10/", Url.withoutFirstPage("https://audioboo.org/page/10/"));
        assertEquals("https://audioboo.org/page/2/", Url.withoutFirstPage("https://audioboo.org/page/2/"));
        assertEquals("https://audioboo.org/index.php?next=/page/1/",
                Url.withoutFirstPage("https://audioboo.org/index.php?next=/page/1/"));
    }

    @Test
    public void canonicalRewritesMovedHostsOnly() {
        assertEquals("https://baza-knig.top/kniga/1.html?x=1", Url.canonical("http://baza-knig.ink/kniga/1.html?x=1"));
        assertEquals("https://www.izib.uk/b/2", Url.canonical("https://www.IZIBUK.ru/b/2"));
        assertEquals("https://akniga.org/a", Url.canonical("https://akniga.org/a"));
        assertEquals("garbage", Url.canonical("garbage"));
        assertEquals("https://user@baza-knig.top:8443/x", Url.canonical("https://user@baza-knig.ink:8443/x"));
    }

    // changed on purpose after the live check (2026-10-30): m.otrub.in and mob.knigoblud.club are the mobile hosts of otrub.in and knigoblud.club
    @Test
    public void canonicalRewritesTheMobileHostsOfOtrubAndKnigoblud() {
        assertEquals("https://otrub.in/book/1", Url.canonical("https://m.otrub.in/book/1"));
        assertEquals("https://otrub.in/popular/?period=month&p=2", Url.canonical("http://M.OTRUB.in/popular/?period=month&p=2"));
        assertEquals("https://knigoblud.club/e87cc048?page=2", Url.canonical("http://mob.knigoblud.club/e87cc048?page=2"));
        assertEquals("https://otrub.in/book/1", Url.canonical("https://otrub.in/book/1"));
        assertEquals("https://www.knigoblud.club/e87cc048", Url.canonical("https://www.knigoblud.club/e87cc048"));
        assertEquals("https://m.otrub.in.evil.com/book/1", Url.canonical("https://m.otrub.in.evil.com/book/1"));
        assertTrue(Url.samePage("https://m.otrub.in/book/1", "https://otrub.in/book/1"));
        assertTrue(Url.samePage("https://mob.knigoblud.club/e87cc048", "https://www.knigoblud.club/e87cc048"));
        assertFalse(Url.samePage("https://m.otrub.in/book/1", "https://knigoblud.club/book/1"));
    }
}
