package com.fanok.audiobooks.policy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Genre;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one parental-control decision: a book is allowed when the control is off, or when the app's genres its site's
 * genre name maps onto ({@link Genre}) are all on the allow-list, the same list for every site. Only a recognised
 * genre can be blocked. The PIN is stored salted-hashed with a lockout.
 */
public final class ParentalControl {

    /** The preference file; the same one the editor and the player's PIN prompt used before. */
    public static final String PREFERENCES = "Parental Control";
    public static final String ENABLED = "Parental Control Enabled";
    /** Legacy plain-int PIN, migrated on the first successful entry. */
    static final String LEGACY_PIN = "Parental Password";
    static final String PIN_HASH = "pin_hash";
    static final String PIN_SALT = "pin_salt";
    static final String PIN_FAILURES = "pin_failures";
    static final String PIN_LOCK_UNTIL = "pin_lock_until";
    static final String PIN_LOCK_SECONDS = "pin_lock_seconds";
    static final String VERSION = "parental_version";

    static final int FAILURES_BEFORE_LOCK = 5;
    static final long FIRST_LOCK_MS = 30_000L;

    /** The allow-list entry of a genre: {@code GENRE_KEY + genre.name()} → allowed. */
    static final String GENRE_KEY = "genre:";

    private ParentalControl() {
    }

    /** The key-value store behind the policy; {@link SharedPreferences} in the app, a map in tests. */
    public interface Store {
        @NonNull
        Map<String, ?> all();

        void putString(@NonNull String key, @NonNull String value);

        void putBoolean(@NonNull String key, boolean value);

        void remove(@NonNull String key);
    }

    /** The result of a PIN check; {@code lockedForMs} is positive only for {@link #LOCKED}. */
    public enum Result {OK, WRONG, LOCKED}

    public static final class Verdict {
        public final Result result;
        public final long lockedForMs;

        Verdict(Result result, long lockedForMs) {
            this.result = result;
            this.lockedForMs = lockedForMs;
        }
    }

    @NonNull
    public static Store store(@NonNull Context context) {
        return new PrefsStore(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE));
    }

    public static boolean isEnabled(@NonNull Context context) {
        return isEnabled(store(context));
    }

    public static boolean isEnabled(@NonNull Store store) {
        Object enabled = store.all().get(ENABLED);
        return enabled instanceof Boolean && (Boolean) enabled;
    }

    /**
     * Whether {@code book} may be shown unmasked and opened without the PIN: when the control is off, or when every
     * genre its genre name is recognised as ({@link Genre#allOf}) is allowed. A genre name that names no known genre,
     * or no genre name at all, cannot be blocked.
     */
    public static boolean isAllowed(@NonNull Context context, @Nullable BookPOJO book) {
        return isAllowed(store(context), book);
    }

    public static boolean isAllowed(@NonNull Store store, @Nullable BookPOJO book) {
        if (!isEnabled(store)) {
            return true;
        }
        return book != null && allowedGenres(store).containsAll(Genre.allOf(book.getGenre()));
    }

    /**
     * The allowed genres. The allow-list of earlier versions, keyed by a site's genre URL or "host|path", is dropped
     * on the way: its genres are now blocked until allowed again, so the update never unblocks anything.
     */
    @NonNull
    public static Set<Genre> allowedGenres(@NonNull Store store) {
        Set<Genre> allowed = EnumSet.noneOf(Genre.class);
        boolean legacy = false;
        for (Map.Entry<String, ?> entry : store.all().entrySet()) {
            String key = entry.getKey();
            if (!(entry.getValue() instanceof Boolean) || ENABLED.equals(key)) {
                continue;
            }
            Genre genre = key.startsWith(GENRE_KEY) ? genre(key.substring(GENRE_KEY.length())) : null;
            if (genre == null) {
                store.remove(key);
                legacy = true;
            } else if ((Boolean) entry.getValue()) {
                allowed.add(genre);
            }
        }
        if (legacy) {
            bumpVersion(store);
        }
        return allowed;
    }

    public static boolean isGenreAllowed(@NonNull Store store, @NonNull Genre genre) {
        return allowedGenres(store).contains(genre);
    }

    public static void setGenreAllowed(@NonNull Store store, @NonNull Genre genre, boolean allowed) {
        if (isGenreAllowed(store, genre) != allowed) {
            store.putBoolean(GENRE_KEY + genre.name(), allowed);
            bumpVersion(store);
        }
    }

    @Nullable
    private static Genre genre(@NonNull String name) {
        try {
            return Genre.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void setEnabled(@NonNull Store store, boolean enabled) {
        if (isEnabled(store) != enabled) {
            store.putBoolean(ENABLED, enabled);
            bumpVersion(store);
        }
    }

    /** Grows on every change of the switch or the allow-list, so screens can tell their covers are stale. */
    public static long version(@NonNull Context context) {
        return version(store(context));
    }

    public static long version(@NonNull Store store) {
        return asLong(store.all().get(VERSION));
    }

    private static void bumpVersion(@NonNull Store store) {
        store.putString(VERSION, String.valueOf(version(store) + 1));
    }

    /** The mask to show instead of {@code book}'s cover, or null when the cover may be shown. */
    @Nullable
    public static Drawable coverMask(@NonNull Context context, @Nullable BookPOJO book) {
        return isAllowed(context, book) ? null : ContextCompat.getDrawable(context, R.drawable.ic_parental_control);
    }

    // ----- PIN -----

    public static boolean hasPin(@NonNull Store store) {
        Map<String, ?> all = store.all();
        return all.get(PIN_HASH) instanceof String || all.get(LEGACY_PIN) instanceof Integer;
    }

    /** Stores {@code pin} salted-hashed and clears any legacy value and lockout. */
    public static void setPin(@NonNull Store store, @NonNull String pin) {
        setPin(store, pin, new SecureRandom());
    }

    static void setPin(@NonNull Store store, @NonNull String pin, @NonNull SecureRandom random) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltHex = hex(salt);
        store.putString(PIN_SALT, saltHex);
        store.putString(PIN_HASH, hash(saltHex, pin));
        store.remove(LEGACY_PIN);
        store.remove(PIN_FAILURES);
        store.remove(PIN_LOCK_UNTIL);
        store.remove(PIN_LOCK_SECONDS);
    }

    public static Verdict verifyPin(@NonNull Context context, @NonNull String pin) {
        return verifyPin(store(context), pin, System.currentTimeMillis());
    }

    /**
     * Checks {@code pin} at time {@code now}. After {@link #FAILURES_BEFORE_LOCK} wrong entries the check is locked
     * for 30 s; every further wrong entry after a lock doubles the next lock. A correct entry resets the schedule
     * and migrates a legacy plain PIN to the hashed form.
     */
    public static Verdict verifyPin(@NonNull Store store, @NonNull String pin, long now) {
        Map<String, ?> all = store.all();
        long lockUntil = asLong(all.get(PIN_LOCK_UNTIL));
        if (lockUntil > now) {
            return new Verdict(Result.LOCKED, lockUntil - now);
        }
        boolean ok;
        Object hash = all.get(PIN_HASH);
        Object salt = all.get(PIN_SALT);
        if (hash instanceof String && salt instanceof String) {
            ok = constantTimeEquals((String) hash, hash((String) salt, pin));
        } else {
            Object legacy = all.get(LEGACY_PIN);
            ok = legacy instanceof Integer && pin.matches("\\d+") && (Integer) legacy == Integer.parseInt(pin);
            if (ok) {
                setPin(store, pin);
            }
        }
        if (ok) {
            store.remove(PIN_FAILURES);
            store.remove(PIN_LOCK_UNTIL);
            store.remove(PIN_LOCK_SECONDS);
            return new Verdict(Result.OK, 0);
        }
        int failures = (int) asLong(all.get(PIN_FAILURES)) + 1;
        store.putString(PIN_FAILURES, String.valueOf(failures));
        if (failures >= FAILURES_BEFORE_LOCK) {
            long previous = asLong(all.get(PIN_LOCK_SECONDS)) * 1000L;
            long lock = previous <= 0 ? FIRST_LOCK_MS : previous * 2;
            store.putString(PIN_LOCK_SECONDS, String.valueOf(lock / 1000L));
            store.putString(PIN_LOCK_UNTIL, String.valueOf(now + lock));
            return new Verdict(Result.LOCKED, lock);
        }
        return new Verdict(Result.WRONG, 0);
    }

    private static long asLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    static String hash(@NonNull String saltHex, @NonNull String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltHex.getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(@NonNull String a, @NonNull String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** A {@link Store} over an in-memory map, for tests and dry runs. */
    public static final class MapStore implements Store {
        private final Map<String, Object> map = new HashMap<>();

        @NonNull
        @Override
        public Map<String, ?> all() {
            return new HashMap<>(map);
        }

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            map.put(key, value);
        }

        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            map.put(key, value);
        }

        @Override
        public void remove(@NonNull String key) {
            map.remove(key);
        }

        public void putInt(@NonNull String key, int value) {
            map.put(key, value);
        }
    }

    private static final class PrefsStore implements Store {
        private final SharedPreferences prefs;

        PrefsStore(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @NonNull
        @Override
        public Map<String, ?> all() {
            return prefs.getAll();
        }

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            prefs.edit().putString(key, value).apply();
        }

        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            prefs.edit().putBoolean(key, value).apply();
        }

        @Override
        public void remove(@NonNull String key) {
            prefs.edit().remove(key).apply();
        }
    }
}
