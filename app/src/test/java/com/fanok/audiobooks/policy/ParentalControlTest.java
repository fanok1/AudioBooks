package com.fanok.audiobooks.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.pojo.BookPOJO;
import java.util.Set;
import org.junit.Test;

public class ParentalControlTest {

    private static BookPOJO book(String url, String genreUrl) {
        BookPOJO book = new BookPOJO();
        book.setUrl(url);
        book.setUrlGenre(genreUrl);
        return book;
    }

    @Test
    public void sameCategoryFromTwoHostsAndWithOrWithoutMarkersIsOneId() {
        String id = ParentalControl.categoryId("https://baza-knig.top/book/1.html", "https://baza-knig.top/fantastika/page/");
        assertEquals("baza-knig.top|/fantastika", id);
        assertEquals(id, ParentalControl.categoryId("https://baza-knig.ink/book/1.html", "https://www.baza-knig.ink/fantastika"));
        assertEquals(id, ParentalControl.categoryId("https://baza-knig.top/book/1.html", "https://baza-knig.top/Fantastika/"));

        String kvu = ParentalControl.categoryId("https://knigavuhe.org/book/x/", "https://knigavuhe.org/genre/fantastika/<page>/");
        assertEquals("knigavuhe.org|/genre/fantastika", kvu);
        assertEquals(kvu, ParentalControl.categoryId("https://m.knigavuhe.org/book/x/", "https://knigavuhe.org/genre/fantastika"));
        assertEquals("akniga.org|/genre/fantastika",
                ParentalControl.categoryId("https://akniga.org/b", "https://akniga.org/genre/fantastika/page<page>/"));
        assertEquals("audiobook-mp3.com|/genres/fantastika",
                ParentalControl.categoryId("https://audiobook-mp3.com/b", "https://audiobook-mp3.com/genres/fantastika?page="));
        assertEquals("izib.uk|/genres/fantastika",
                ParentalControl.categoryId("https://izibuk.ru/b/1", "https://izib.uk/genres/fantastika?p="));
    }

    @Test
    public void unknownGenreHasNoId() {
        assertNull(ParentalControl.categoryId("https://knigavuhe.org/book/x/", null));
        assertNull(ParentalControl.categoryId("https://knigavuhe.org/book/x/", "https://knigavuhe.org/"));
        assertNull(ParentalControl.categoryId(null, null));
    }

    @Test
    public void unknownGenreIsBlockedWhenEnabledAndAllowedWhenDisabled() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        BookPOJO noGenre = book("https://knigavuhe.org/book/x/", null);
        BookPOJO allowed = book("https://knigavuhe.org/book/y/", "https://knigavuhe.org/genre/detskie/<page>/");
        BookPOJO other = book("https://knigavuhe.org/book/z/", "https://knigavuhe.org/genre/uzhasy/<page>/");

        assertTrue(ParentalControl.isAllowed(store, noGenre));
        assertTrue(ParentalControl.isAllowed(store, other));

        store.putBoolean(ParentalControl.ENABLED, true);
        ParentalControl.setCategoryAllowed(store, "https://knigavuhe.org/genre/detskie/<page>/", true);
        assertFalse(ParentalControl.isAllowed(store, noGenre));
        assertFalse(ParentalControl.isAllowed(store, null));
        assertTrue(ParentalControl.isAllowed(store, allowed));
        assertFalse(ParentalControl.isAllowed(store, other));
    }

    @Test
    public void legacyGenreUrlEntriesAreNormalisedOnRead() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putBoolean(ParentalControl.ENABLED, true);
        store.putBoolean("https://knigavuhe.org/genre/detskie/<page>/", true);
        store.putBoolean("https://baza-knig.ink/fantastika/page/", false);

        Set<String> ids = ParentalControl.allowedIds(store);
        assertEquals(1, ids.size());
        assertTrue(ids.contains("knigavuhe.org|/genre/detskie"));
        assertTrue(ParentalControl.isAllowed(store,
                book("https://knigavuhe.org/book/y/", "https://knigavuhe.org/genre/detskie/")));
        assertTrue(ParentalControl.isCategoryAllowed(store, "https://knigavuhe.org/genre/detskie/<page>/"));
        assertFalse(ParentalControl.isCategoryAllowed(store, "https://baza-knig.top/fantastika/"));
        // migrated: raw URLs are gone, the normalised ids remain
        for (String key : store.all().keySet()) {
            assertFalse(key, key.startsWith("http"));
        }
        assertEquals(Boolean.FALSE, store.all().get("baza-knig.top|/fantastika"));
    }

    @Test
    public void conflictingLegacyAliasesMigrateToDeny() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putBoolean(ParentalControl.ENABLED, true);
        store.putBoolean("https://baza-knig.ink/fantastika/page/", true);
        store.putBoolean("https://baza-knig.top/fantastika/page/", false);
        BookPOJO fantastika = book("https://baza-knig.top/book/1.html", "https://baza-knig.top/fantastika/");

        assertTrue(ParentalControl.allowedIds(store).isEmpty());
        assertFalse(ParentalControl.isAllowed(store, fantastika));
        assertEquals(Boolean.FALSE, store.all().get("baza-knig.top|/fantastika"));
        assertEquals(2, store.all().size());
    }

    @Test
    public void legacyAllowCannotOverrideCanonicalDeny() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putBoolean(ParentalControl.ENABLED, true);
        store.putBoolean("knigavuhe.org|/genre/uzhasy", false);
        store.putBoolean("https://knigavuhe.org/genre/uzhasy/<page>/", true);

        assertFalse(ParentalControl.isAllowed(store,
                book("https://knigavuhe.org/book/z/", "https://knigavuhe.org/genre/uzhasy/")));
        assertEquals(Boolean.FALSE, store.all().get("knigavuhe.org|/genre/uzhasy"));
        assertFalse(store.all().containsKey("https://knigavuhe.org/genre/uzhasy/<page>/"));
    }

    @Test
    public void sameCategoryPathOnTwoProvidersIsTwoIds() {
        String audioboo = ParentalControl.categoryId("https://audioboo.org/b/1", "https://audioboo.org/fantastika/page/");
        String baza = ParentalControl.categoryId("https://baza-knig.top/b/1", "https://baza-knig.top/fantastika/page/");
        assertEquals("audioboo.org|/fantastika", audioboo);
        assertEquals("baza-knig.top|/fantastika", baza);

        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putBoolean(ParentalControl.ENABLED, true);
        ParentalControl.setCategoryAllowed(store, "https://audioboo.org/fantastika/page/", true);
        assertTrue(ParentalControl.isAllowed(store, book("https://audioboo.org/b/1", "https://audioboo.org/fantastika/")));
        assertFalse(ParentalControl.isAllowed(store,
                book("https://baza-knig.top/b/1", "https://baza-knig.top/fantastika/")));
    }

    @Test
    public void versionGrowsOnlyWhenSwitchOrAllowListChanges() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        assertEquals(0, ParentalControl.version(store));
        ParentalControl.setEnabled(store, true);
        assertEquals(1, ParentalControl.version(store));
        ParentalControl.setEnabled(store, true);
        assertEquals(1, ParentalControl.version(store));
        ParentalControl.setCategoryAllowed(store, "https://knigavuhe.org/genre/detskie/<page>/", true);
        ParentalControl.setCategoryAllowed(store, "https://knigavuhe.org/genre/detskie/", true);
        assertEquals(2, ParentalControl.version(store));
        ParentalControl.setCategoryAllowed(store, "https://knigavuhe.org/genre/detskie/", false);
        ParentalControl.setEnabled(store, false);
        assertEquals(4, ParentalControl.version(store));
        assertTrue(ParentalControl.allowedIds(store).isEmpty());
    }

    @Test
    public void hashedPinVerifiesAndLockoutDoubles() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        ParentalControl.setPin(store, "1234");
        assertTrue(ParentalControl.hasPin(store));
        assertFalse(store.all().containsValue("1234"));

        long now = 1_000_000L;
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "1234", now).result);
        for (int i = 0; i < 4; i++) {
            assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "0000", now).result);
        }
        ParentalControl.Verdict locked = ParentalControl.verifyPin(store, "0000", now);
        assertEquals(ParentalControl.Result.LOCKED, locked.result);
        assertEquals(30_000L, locked.lockedForMs);
        // the right PIN is refused while locked
        ParentalControl.Verdict still = ParentalControl.verifyPin(store, "1234", now + 10_000L);
        assertEquals(ParentalControl.Result.LOCKED, still.result);
        assertEquals(20_000L, still.lockedForMs);
        // after the lock, the next wrong entry locks for twice as long
        ParentalControl.Verdict second = ParentalControl.verifyPin(store, "0000", now + 30_000L);
        assertEquals(ParentalControl.Result.LOCKED, second.result);
        assertEquals(60_000L, second.lockedForMs);
        ParentalControl.Verdict third = ParentalControl.verifyPin(store, "0000", now + 90_000L);
        assertEquals(120_000L, third.lockedForMs);
        // a correct entry resets the schedule
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "1234", now + 210_000L).result);
        for (int i = 0; i < 4; i++) {
            assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "0000", now + 210_000L).result);
        }
        assertEquals(30_000L, ParentalControl.verifyPin(store, "0000", now + 210_000L).lockedForMs);
    }

    @Test
    public void legacyPlainPinIsMigratedOnFirstSuccess() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putInt(ParentalControl.LEGACY_PIN, 4321);
        assertTrue(ParentalControl.hasPin(store));
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "1111", 0).result);
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "4321", 0).result);
        assertFalse(store.all().containsKey(ParentalControl.LEGACY_PIN));
        assertTrue(store.all().containsKey(ParentalControl.PIN_HASH));
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "4321", 0).result);
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "1111", 0).result);
    }

    @Test
    public void legacyZeroPinAcceptsZerosAndRejectsEmpty() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putInt(ParentalControl.LEGACY_PIN, 0);
        assertTrue(ParentalControl.hasPin(store));
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "", 0).result);
        assertTrue(store.all().containsKey(ParentalControl.LEGACY_PIN));
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "0000", 0).result);
        assertFalse(store.all().containsKey(ParentalControl.LEGACY_PIN));
        // once hashed, the PIN is the exact string that was entered
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "0000", 0).result);
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "", 0).result);
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "0", 0).result);
    }

    @Test
    public void emptyStoreHasNoPinAndRejectsEveryEntry() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        assertFalse(ParentalControl.hasPin(store));
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "", 0).result);
        assertEquals(ParentalControl.Result.WRONG, ParentalControl.verifyPin(store, "0000", 0).result);
    }
}
