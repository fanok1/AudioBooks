package com.fanok.audiobooks.room;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.fanok.audiobooks.Consts;
import java.util.HashSet;
import java.util.Set;

@Database(entities = {
    FavoriteEntity.class,
    HistoryEntity.class,
    SavedEntity.class,
    AudioEntity.class,
    BooksAudioEntity.class
}, version = 18)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
    public abstract HistoryDao historyDao();
    public abstract SavedDao savedDao();
    public abstract AudioDao audioDao();
    public abstract BooksAudioDao booksAudioDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, Consts.DBName)
                            .addMigrations(MIGRATIONS)
                            // No schema is known for versions before 15, so only those (and downgrades) start empty.
                            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
                            .fallbackToDestructiveMigrationOnDowngrade(true)
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
             database.execSQL("CREATE TABLE IF NOT EXISTS `saved` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)");
             database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_url_book` ON `saved` (`url_book`)");
        }
    };

    private static final String BOOK_COLUMNS = "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)";

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            rebuildTable(database, "favorite", BOOK_COLUMNS, true);
            rebuildTable(database, "history", BOOK_COLUMNS, true);
            rebuildTable(database, "saved", BOOK_COLUMNS, true);
            rebuildTable(database, "audio",
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url_book` TEXT NOT NULL, `name` TEXT NOT NULL, `time` INTEGER NOT NULL DEFAULT 0)",
                true);
            rebuildTable(database, "books_audio",
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url_book` TEXT NOT NULL, `books_name` TEXT NOT NULL, `name_audio` TEXT, `url_audio` TEXT, `time` INTEGER NOT NULL DEFAULT 0, `time_start` INTEGER NOT NULL DEFAULT -1, `time_end` INTEGER NOT NULL DEFAULT -1)",
                false);
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `books_audio` ADD COLUMN `local_uri` TEXT");
            database.execSQL("ALTER TABLE `books_audio` ADD COLUMN `ordinal` INTEGER NOT NULL DEFAULT -1");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_audio_url_book` ON `books_audio` (`url_book`)");
            // Rows the old code rewrote with a local path are NOT claimed as app-written copies: that path may have
            // been found by the old name-matching repair, so provenance is unknown and such files are never deleted.
        }
    };

    /** Every upgrade path, chained; the builder and the migration tests use the same list. */
    static final Migration[] MIGRATIONS = {MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18};

    /**
     * Recreates {@code table} with {@code columns}. The new table is filled under a temporary name and gets
     * the old name (and its index) only after the old table is dropped, so the index is never skipped because the
     * old table still owns one of that name. Rows without a book URL cannot be kept; when the URL must be unique,
     * only the newest row (highest rowid) per URL is kept. Any SQL error propagates and rolls the migration back.
     */
    private static void rebuildTable(SupportSQLiteDatabase db, String table, String columns, boolean uniqueUrl) {
        String temp = table + "_new";
        db.execSQL("DROP TABLE IF EXISTS `" + temp + "`");
        db.execSQL("CREATE TABLE `" + temp + "` " + columns);
        if (tableExists(db, table)) {
            if (uniqueUrl) {
                db.execSQL("DELETE FROM `" + table + "` WHERE rowid NOT IN (SELECT MAX(rowid) FROM `" + table
                        + "` GROUP BY `url_book`)");
            }
            copyRows(db, table, temp);
            db.execSQL("DROP TABLE `" + table + "`");
        }
        db.execSQL("ALTER TABLE `" + temp + "` RENAME TO `" + table + "`");
        if (uniqueUrl) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_" + table + "_url_book` ON `" + table + "` (`url_book`)");
        }
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String table) {
        try (Cursor c = db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new Object[]{table})) {
            return c.moveToFirst();
        }
    }

    /**
     * Copies the columns both tables share; a NULL in a column that is NOT NULL in {@code to} becomes that
     * column's default (or '' when it has none) instead of failing the copy.
     */
    private static void copyRows(SupportSQLiteDatabase db, String from, String to) {
        Set<String> oldColumns = new HashSet<>();
        try (Cursor c = db.query("PRAGMA table_info(`" + from + "`)")) {
            while (c.moveToNext()) {
                oldColumns.add(c.getString(c.getColumnIndexOrThrow("name")));
            }
        }
        StringBuilder names = new StringBuilder();
        StringBuilder values = new StringBuilder();
        try (Cursor c = db.query("PRAGMA table_info(`" + to + "`)")) {
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                if (!oldColumns.contains(name)) continue;
                String column = "`" + name + "`";
                boolean notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) != 0
                        && c.getInt(c.getColumnIndexOrThrow("pk")) == 0;
                int defaultIndex = c.getColumnIndexOrThrow("dflt_value");
                String fallback = c.isNull(defaultIndex) ? "''" : c.getString(defaultIndex);
                if (names.length() > 0) {
                    names.append(", ");
                    values.append(", ");
                }
                names.append(column);
                values.append(notNull ? "COALESCE(" + column + ", " + fallback + ")" : column);
            }
        }
        db.execSQL("INSERT INTO `" + to + "` (" + names + ") SELECT " + values + " FROM `" + from
                + "` WHERE `url_book` IS NOT NULL");
    }
}
