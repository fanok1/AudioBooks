package com.fanok.audiobooks.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Genre;
import java.util.Collections;
import org.junit.Test;

public class ParentalControlTest {

    private static BookPOJO book(String genre) {
        BookPOJO book = new BookPOJO();
        book.setUrl("https://knigavuhe.org/book/x/");
        if (genre != null) {
            book.setGenre(genre);
        }
        return book;
    }

    private static ParentalControl.MapStore enabled() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        store.putBoolean(ParentalControl.ENABLED, true);
        return store;
    }

    @Test
    public void everythingIsAllowedWhenDisabled() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        assertTrue(ParentalControl.isAllowed(store, book("Ужасы, мистика")));
        assertTrue(ParentalControl.isAllowed(store, null));
    }

    @Test
    public void onlyARecognisedGenreCanBeBlocked() {
        ParentalControl.MapStore store = enabled();
        assertFalse(ParentalControl.isAllowed(store, book("Ужасы, мистика")));
        assertFalse(ParentalControl.isAllowed(store, book("Разное")));
        // a name that names no known genre, or none at all, passes as it is
        assertTrue(ParentalControl.isAllowed(store, book("Искусственный голос")));
        assertTrue(ParentalControl.isAllowed(store, book(null)));
        assertFalse(ParentalControl.isAllowed(store, null));
    }

    @Test
    public void oneSwitchCoversTheGenreOnEverySite() {
        ParentalControl.MapStore store = enabled();
        ParentalControl.setGenreAllowed(store, Genre.KIDS, true);
        assertTrue(ParentalControl.isAllowed(store, book("Аудиосказки и рассказы для детей")));
        assertTrue(ParentalControl.isAllowed(store, book("Для детей и Аудиосказки")));
        assertTrue(ParentalControl.isAllowed(store, book("Сказки")));
        assertFalse(ParentalControl.isAllowed(store, book("Мистика и ужасы")));
        assertEquals(Collections.singleton(Genre.KIDS), ParentalControl.allowedGenres(store));
    }

    @Test
    public void aNameOfSeveralGenresNeedsThemAll() {
        ParentalControl.MapStore store = enabled();
        ParentalControl.setGenreAllowed(store, Genre.DETECTIVE, true);
        assertTrue(ParentalControl.isAllowed(store, book("Детективы, триллеры")));
        assertFalse(ParentalControl.isAllowed(store, book("Детективы, триллеры, боевики")));
        ParentalControl.setGenreAllowed(store, Genre.ACTION, true);
        assertTrue(ParentalControl.isAllowed(store, book("Детективы, триллеры, боевики")));

        ParentalControl.setGenreAllowed(store, Genre.KIDS, true);
        // erotica anywhere in the name is one of its genres
        assertFalse(ParentalControl.isAllowed(store, book("Сказки для взрослых")));
    }

    @Test
    public void theAllowListOfEarlierVersionsIsDroppedAndBlocksAgain() {
        ParentalControl.MapStore store = enabled();
        ParentalControl.setPin(store, "1234");
        store.putBoolean("https://knigavuhe.org/genre/detskie/<page>/", true);
        store.putBoolean("knigavuhe.org|/genre/detskie", true);
        store.putBoolean(ParentalControl.GENRE_KEY + "NO_SUCH_GENRE", true);
        long version = ParentalControl.version(store);

        assertFalse(ParentalControl.isAllowed(store, book("Для детей")));
        assertTrue(ParentalControl.allowedGenres(store).isEmpty());
        for (String key : store.all().keySet()) {
            assertFalse(key, key.contains("|") || key.startsWith("http") || key.endsWith("NO_SUCH_GENRE"));
        }
        assertEquals(version + 1, ParentalControl.version(store));
        assertTrue(ParentalControl.isEnabled(store));
        assertEquals(ParentalControl.Result.OK, ParentalControl.verifyPin(store, "1234", 0).result);
    }

    @Test
    public void versionGrowsOnlyWhenSwitchOrAllowListChanges() {
        ParentalControl.MapStore store = new ParentalControl.MapStore();
        assertEquals(0, ParentalControl.version(store));
        ParentalControl.setEnabled(store, true);
        assertEquals(1, ParentalControl.version(store));
        ParentalControl.setEnabled(store, true);
        assertEquals(1, ParentalControl.version(store));
        ParentalControl.setGenreAllowed(store, Genre.KIDS, true);
        ParentalControl.setGenreAllowed(store, Genre.KIDS, true);
        assertEquals(2, ParentalControl.version(store));
        assertTrue(ParentalControl.isGenreAllowed(store, Genre.KIDS));
        ParentalControl.setGenreAllowed(store, Genre.KIDS, false);
        ParentalControl.setEnabled(store, false);
        assertEquals(4, ParentalControl.version(store));
        assertTrue(ParentalControl.allowedGenres(store).isEmpty());
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
