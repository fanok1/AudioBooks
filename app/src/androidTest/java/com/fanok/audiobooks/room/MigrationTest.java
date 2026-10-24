package com.fanok.audiobooks.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Upgrades real databases through the app's own migrations. The version-16 fixture is written with raw SQL:
 * no schema file exists for it, so its tables use the columns the 16-to-17 migration copies, with looser
 * constraints, and the unique indexes a Room-era database already had (favorite, saved, audio).
 */
public class MigrationTest {
    private static final String DB = "migration-test.db";

    @Rule
    public final MigrationTestHelper helper =
            new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DB);
    }

    @Test
    public void migrate16To18KeepsRowsAndIndexes() {
        try (SQLiteDatabase db = createVersion16()) {
            insertBook(db, "favorite", "Favorite", "https://example.com/f1");
            insertBook(db, "favorite", "Favorite 2", "https://example.com/f2");
            insertBook(db, "history", "History", "https://example.com/h1");
            insertBook(db, "saved", "Saved", "https://example.com/s1");
            db.execSQL("INSERT INTO audio (url_book, name, time) VALUES ('https://example.com/h1', 'Chapter 3', 120)");
            db.execSQL("INSERT INTO audio (url_book, name, time) VALUES ('https://example.com/f1', 'Chapter 1', NULL)");
            db.execSQL("INSERT INTO books_audio (url_book, books_name, name_audio, url_audio, time)"
                    + " VALUES ('https://example.com/s1', 'Saved', 'Chapter 1', 'https://example.com/1.mp3', NULL)");
            db.execSQL("INSERT INTO books_audio (url_book, books_name, name_audio, url_audio)"
                    + " VALUES ('https://example.com/s1', 'Saved', 'Chapter 2', 'https://example.com/2.mp3')");
        }

        AppDatabase room = openWithRoom();
        try {
            SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
            assertEquals(18, db.getVersion());
            assertEquals(2, count(db, "favorite"));
            assertEquals(1, count(db, "history"));
            assertEquals(1, count(db, "saved"));
            assertEquals(2, count(db, "audio"));
            assertEquals(2, count(db, "books_audio"));
            for (String table : new String[]{"favorite", "history", "saved", "audio"}) {
                assertEquals(table, Boolean.TRUE, indexes(db, table).get("index_" + table + "_url_book"));
            }
            assertEquals(Boolean.FALSE, indexes(db, "books_audio").get("index_books_audio_url_book"));

            assertEquals(2, room.favoriteDao().getAll().size());
            assertEquals("History", room.historyDao().getByUrl("https://example.com/h1").name);
            assertEquals(120, room.audioDao().getTime("https://example.com/h1"));
            assertEquals(0, room.audioDao().getTime("https://example.com/f1"));
            List<BooksAudioEntity> tracks = room.booksAudioDao().getByBookUrl("https://example.com/s1");
            assertEquals(2, tracks.size());
            for (BooksAudioEntity track : tracks) {
                assertEquals(0, track.time);
                assertEquals(-1, track.ordinal);
                assertNull(track.localUri);
            }
        } finally {
            room.close();
        }
    }

    @Test
    public void migrate16KeepsNewestOfDuplicateUrls() {
        try (SQLiteDatabase db = createVersion16()) {
            insertBook(db, "history", "Old", "https://example.com/dup");
            insertBook(db, "history", "Newest", "https://example.com/dup");
            insertBook(db, "history", "Other", "https://example.com/other");
            db.execSQL("INSERT INTO books_audio (url_book, books_name) VALUES (NULL, 'Orphan')");
        }

        AppDatabase room = openWithRoom();
        try {
            SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
            assertEquals(2, count(db, "history"));
            assertEquals("Newest", room.historyDao().getByUrl("https://example.com/dup").name);
            assertEquals(0, count(db, "books_audio"));
        } finally {
            room.close();
        }
    }

    @Test
    public void migrate17To18AddsColumnsAndIndex() throws Exception {
        try (SupportSQLiteDatabase db = helper.createDatabase(DB, 17)) {
            db.execSQL("INSERT INTO books_audio (url_book, books_name, name_audio, url_audio)"
                    + " VALUES ('https://example.com/b', 'Book', 'Chapter', 'https://example.com/b.m3u8')");
        }

        SupportSQLiteDatabase db = helper.runMigrationsAndValidate(DB, 18, true, AppDatabase.MIGRATION_17_18);
        try (Cursor c = db.query("SELECT local_uri, ordinal FROM books_audio")) {
            assertTrue(c.moveToFirst());
            assertTrue(c.isNull(0));
            assertEquals(-1, c.getInt(1));
        }
        assertEquals(Boolean.FALSE, indexes(db, "books_audio").get("index_books_audio_url_book"));
    }

    private SQLiteDatabase createVersion16() {
        SQLiteDatabase db = context.openOrCreateDatabase(DB, Context.MODE_PRIVATE, null);
        String book = " (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `url_book` TEXT, `photo` TEXT,"
                + " `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT,"
                + " `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT,"
                + " `coments` INTEGER, `description` TEXT)";
        db.execSQL("CREATE TABLE `favorite`" + book);
        db.execSQL("CREATE UNIQUE INDEX `index_favorite_url_book` ON `favorite` (`url_book`)");
        db.execSQL("CREATE TABLE `history`" + book);
        db.execSQL("CREATE TABLE `saved`" + book);
        db.execSQL("CREATE UNIQUE INDEX `index_saved_url_book` ON `saved` (`url_book`)");
        db.execSQL("CREATE TABLE `audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url_book` TEXT,"
                + " `name` TEXT, `time` INTEGER)");
        db.execSQL("CREATE UNIQUE INDEX `index_audio_url_book` ON `audio` (`url_book`)");
        db.execSQL("CREATE TABLE `books_audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `url_book` TEXT,"
                + " `books_name` TEXT, `name_audio` TEXT, `url_audio` TEXT, `time` INTEGER,"
                + " `time_start` INTEGER, `time_end` INTEGER)");
        db.setVersion(16);
        return db;
    }

    private static void insertBook(SQLiteDatabase db, String table, String name, String url) {
        db.execSQL("INSERT INTO " + table + " (name, url_book, coments) VALUES (?, ?, 0)", new Object[]{name, url});
    }

    private AppDatabase openWithRoom() {
        return Room.databaseBuilder(context, AppDatabase.class, DB)
                .addMigrations(AppDatabase.MIGRATIONS)
                .allowMainThreadQueries()
                .build();
    }

    private static int count(SupportSQLiteDatabase db, String table) {
        try (Cursor c = db.query("SELECT COUNT(*) FROM " + table)) {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        }
    }

    /** Index name to "is unique", from {@code PRAGMA index_list}. */
    private static Map<String, Boolean> indexes(SupportSQLiteDatabase db, String table) {
        Map<String, Boolean> result = new HashMap<>();
        try (Cursor c = db.query("PRAGMA index_list(`" + table + "`)")) {
            while (c.moveToNext()) {
                result.put(c.getString(c.getColumnIndexOrThrow("name")),
                        c.getInt(c.getColumnIndexOrThrow("unique")) != 0);
            }
        }
        return result;
    }
}
