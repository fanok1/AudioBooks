package com.fanok.audiobooks.pojo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public class BackupPOJO {
    /** Format this app writes. Version 1 is the earlier format, which carried no version field. */
    public static final int VERSION = 2;
    private static final int LEGACY_VERSION = 1;
    private static final String KEY_VERSION = "mVersion";
    private static final String[] COLLECTIONS = {
            "mBooksFavorite", "mBooksHistory", "mBooksSaved", "mAudio", "mAudioList"};

    private int mVersion = VERSION;
    private ArrayList<BookPOJO> mBooksFavorite;
    private ArrayList<BookPOJO> mBooksHistory;
    private ArrayList<BookPOJO> mBooksSaved;
    private ArrayList<TimeStartPOJO> mAudio;
    private ArrayList<AudioListPOJO> mAudioList;
    /** Rows left out of this backup because they could not be read; not written to the file. */
    private transient int mSkippedRows;

    /** The document is not a restorable backup. Thrown before anything has been written. */
    public static class InvalidBackupException extends Exception {
        InvalidBackupException(String message) {
            super(message);
        }

        InvalidBackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parses and validates a backup document. Every item of every collection must be storable as is. A
     * collection that is absent (or null) is returned as null and means "leave that table alone"; an empty one
     * means "clear it". A document without a version field is accepted as version 1 only when it carries at least
     * one known collection, so {@code {}} and unrelated JSON are rejected.
     */
    public static BackupPOJO parse(String json) throws InvalidBackupException {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidBackupException("Backup is empty");
        }
        JsonObject root;
        BackupPOJO backup;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new InvalidBackupException("Backup is not a JSON object");
            }
            root = element.getAsJsonObject();
            backup = new Gson().fromJson(root, BackupPOJO.class);
        } catch (RuntimeException e) {
            throw new InvalidBackupException("Backup is malformed", e);
        }

        boolean hasCollection = false;
        for (String key : COLLECTIONS) {
            hasCollection |= root.has(key) && !root.get(key).isJsonNull();
        }
        if (!hasCollection) {
            throw new InvalidBackupException("Backup contains no known collection");
        }
        JsonElement version = root.get(KEY_VERSION);
        if (version == null) {
            backup.mVersion = LEGACY_VERSION;
        } else if (!version.isJsonPrimitive()) {
            throw new InvalidBackupException("Backup version is not a number");
        }
        if (backup.mVersion < LEGACY_VERSION || backup.mVersion > VERSION) {
            throw new InvalidBackupException("Unsupported backup version " + backup.mVersion);
        }

        validateBooks("mBooksFavorite", backup.mBooksFavorite);
        validateBooks("mBooksHistory", backup.mBooksHistory);
        validateBooks("mBooksSaved", backup.mBooksSaved);
        if (backup.mAudio != null) {
            for (int i = 0; i < backup.mAudio.size(); i++) {
                TimeStartPOJO audio = backup.mAudio.get(i);
                require(audio != null && notBlank(audio.getUrl()) && audio.getName() != null
                        && audio.getTime() >= 0, "mAudio", i);
            }
        }
        if (backup.mAudioList != null) {
            for (int i = 0; i < backup.mAudioList.size(); i++) {
                AudioListPOJO audio = backup.mAudioList.get(i);
                require(audio != null && notBlank(audio.getBookUrl()) && audio.getBookName() != null
                        && audio.getTime() >= 0, "mAudioList", i);
            }
        }
        return backup;
    }

    private static void validateBooks(String key, List<BookPOJO> books) throws InvalidBackupException {
        if (books == null) {
            return;
        }
        for (int i = 0; i < books.size(); i++) {
            BookPOJO book = books.get(i);
            require(book != null && !book.isNull() && notBlank(book.getUrl()) && notBlank(book.getName()), key, i);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void require(boolean valid, String key, int index) throws InvalidBackupException {
        if (!valid) {
            throw new InvalidBackupException("Invalid item " + index + " in " + key);
        }
    }

    public int getVersion() {
        return mVersion;
    }

    public int getSkippedRows() {
        return mSkippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        mSkippedRows = skippedRows;
    }

    public ArrayList<AudioListPOJO> getAudioList() {
        return mAudioList;
    }

    public void setAudioList(ArrayList<AudioListPOJO> audioList) {
        mAudioList = audioList;
    }

    public ArrayList<BookPOJO> getBooksFavorite() {
        return mBooksFavorite;
    }

    public void setBooksFavorite(ArrayList<BookPOJO> booksFavorite) {
        mBooksFavorite = booksFavorite;
    }

    public ArrayList<BookPOJO> getBooksHistory() {
        return mBooksHistory;
    }

    public void setBooksHistory(ArrayList<BookPOJO> booksHistory) {
        mBooksHistory = booksHistory;
    }

    public ArrayList<BookPOJO> getBooksSaved() {
        return mBooksSaved;
    }

    public void setBooksSaved(ArrayList<BookPOJO> booksSaved) {
        mBooksSaved = booksSaved;
    }

    public ArrayList<TimeStartPOJO> getAudio() {
        return mAudio;
    }

    public void setAudio(ArrayList<TimeStartPOJO> audio) {
        mAudio = audio;
    }
}
