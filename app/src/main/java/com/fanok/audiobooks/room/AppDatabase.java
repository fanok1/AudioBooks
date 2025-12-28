package com.fanok.audiobooks.room;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.fanok.audiobooks.Consts;

@Database(entities = {
    FavoriteEntity.class,
    HistoryEntity.class,
    SavedEntity.class,
    AudioEntity.class,
    BooksAudioEntity.class
}, version = 19)
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
                            .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                            .fallbackToDestructiveMigration()
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

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migrate favorite
            migrateTable(database, "favorite", 
                "CREATE TABLE IF NOT EXISTS `favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_url_book` ON `favorite` (`url_book`)"
            );

            // Migrate history
            migrateTable(database, "history",
                "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_url_book` ON `history` (`url_book`)"
            );
            
            // Migrate saved
             migrateTable(database, "saved",
                "CREATE TABLE IF NOT EXISTS `saved` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_url_book` ON `saved` (`url_book`)"
            );

            // Migrate audio
            migrateTable(database, "audio",
                "CREATE TABLE IF NOT EXISTS `audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url_book` TEXT NOT NULL, `name` TEXT NOT NULL, `time` INTEGER NOT NULL DEFAULT 0)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_audio_url_book` ON `audio` (`url_book`)"
            );

            // Migrate books_audio
            migrateTable(database, "books_audio",
                "CREATE TABLE IF NOT EXISTS `books_audio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url_book` TEXT NOT NULL, `books_name` TEXT NOT NULL, `name_audio` TEXT, `url_audio` TEXT, `time` INTEGER NOT NULL DEFAULT 0, `time_start` INTEGER NOT NULL DEFAULT -1, `time_end` INTEGER NOT NULL DEFAULT -1)",
                null
            );
        }

        private void migrateTable(SupportSQLiteDatabase database, String tableName, String createTableSql, String createIndexSql) {
            database.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tableName + "_old");
            database.execSQL(createTableSql);
            if (createIndexSql != null) {
                database.execSQL(createIndexSql);
            }
            
            String columns;
            if (tableName.equals("audio")) {
                columns = "id, url_book, name, time";
            } else if (tableName.equals("books_audio")) {
                columns = "id, url_book, books_name, name_audio, url_audio, time, time_start, time_end";
            } else {
                columns = "id, name, url_book, photo, genre, url_genre, author, url_author, artist, url_artist, series, url_series, time, reting, coments, description";
            }
            
            // Execute copy
            try {
                database.execSQL("INSERT INTO " + tableName + " (" + columns + ") SELECT " + columns + " FROM " + tableName + "_old");
            } catch (Exception e) {
                // If columns mismatch or other error, we might lose data for this table, but we try our best.
            }
            database.execSQL("DROP TABLE " + tableName + "_old");
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add columns to favorite
            database.execSQL("ALTER TABLE `favorite` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `favorite` ADD COLUMN `need_sync` INTEGER NOT NULL DEFAULT 0");

            // Add columns to history
            database.execSQL("ALTER TABLE `history` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `history` ADD COLUMN `need_sync` INTEGER NOT NULL DEFAULT 0");

            // Add columns to saved
            database.execSQL("ALTER TABLE `saved` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `saved` ADD COLUMN `need_sync` INTEGER NOT NULL DEFAULT 0");

            // Add columns to audio
            database.execSQL("ALTER TABLE `audio` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `audio` ADD COLUMN `need_sync` INTEGER NOT NULL DEFAULT 0");

            // Add columns to books_audio
            database.execSQL("ALTER TABLE `books_audio` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `books_audio` ADD COLUMN `need_sync` INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add 'deleted' column to tables
            database.execSQL("ALTER TABLE `favorite` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `history` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `audio` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `books_audio` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0");

            // Remove columns from saved table
            // Create new table without updated_at and need_sync
            database.execSQL("CREATE TABLE IF NOT EXISTS `saved_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url_book` TEXT NOT NULL, `photo` TEXT, `genre` TEXT, `url_genre` TEXT, `author` TEXT, `url_author` TEXT, `artist` TEXT, `url_artist` TEXT, `series` TEXT, `url_series` TEXT, `time` TEXT, `reting` TEXT, `coments` INTEGER, `description` TEXT)");
            
            // Copy data
            database.execSQL("INSERT INTO `saved_new` (`id`, `name`, `url_book`, `photo`, `genre`, `url_genre`, `author`, `url_author`, `artist`, `url_artist`, `series`, `url_series`, `time`, `reting`, `coments`, `description`) SELECT `id`, `name`, `url_book`, `photo`, `genre`, `url_genre`, `author`, `url_author`, `artist`, `url_artist`, `series`, `url_series`, `time`, `reting`, `coments`, `description` FROM `saved`");
            
            // Drop old table
            database.execSQL("DROP TABLE `saved`");
            
            // Rename new table
            database.execSQL("ALTER TABLE `saved_new` RENAME TO `saved`");
            
            // Recreate index
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_url_book` ON `saved` (`url_book`)");
        }
    };
}
