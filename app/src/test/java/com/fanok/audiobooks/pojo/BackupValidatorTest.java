package com.fanok.audiobooks.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class BackupValidatorTest {

    private static final String BOOK = "{\"name\":\"Book\",\"url\":\"https://example.com/b\",\"coments\":0}";
    private static final String AUDIO = "{\"name\":\"Chapter\",\"url\":\"https://example.com/b\",\"time\":5}";
    private static final String TRACK = "{\"bookUrl\":\"https://example.com/b\",\"bookName\":\"Book\","
            + "\"audioName\":\"Chapter\",\"audioUrl\":\"https://example.com/1.mp3\",\"time\":3}";

    @Test
    public void rejectsNullAndEmpty() {
        assertRejected(null);
        assertRejected("");
        assertRejected("  \n");
    }

    @Test
    public void rejectsDocumentsThatAreNotBackups() {
        assertRejected("{}");
        assertRejected("{\"name\":\"settings\",\"items\":[1,2]}");
        assertRejected("[" + BOOK + "]");
        assertRejected("\"text\"");
        assertRejected("not json {");
        assertRejected("{\"mBooksFavorite\":null}");
    }

    @Test
    public void rejectsMissingSupportOrMalformedVersion() {
        assertRejected("{\"mVersion\":99,\"mBooksFavorite\":[]}");
        assertRejected("{\"mVersion\":0,\"mBooksFavorite\":[]}");
        assertRejected("{\"mVersion\":\"two\",\"mBooksFavorite\":[]}");
        assertRejected("{\"mVersion\":null,\"mBooksFavorite\":[]}");
        assertRejected("{\"mVersion\":2}");
    }

    @Test
    public void rejectsCollectionWithOneMalformedItem() {
        assertRejected("{\"mVersion\":2,\"mBooksFavorite\":[" + BOOK + ",{\"url\":\"https://example.com/x\"}]}");
        assertRejected("{\"mVersion\":2,\"mBooksHistory\":[" + BOOK + ",null]}");
        assertRejected("{\"mVersion\":2,\"mBooksSaved\":[{\"name\":\"No url\"}]}");
        assertRejected("{\"mVersion\":2,\"mAudio\":[" + AUDIO + ",{\"url\":\"https://example.com/x\",\"name\":null}]}");
        assertRejected("{\"mVersion\":2,\"mAudio\":[{\"url\":\"https://example.com/x\",\"name\":\"c\",\"time\":-1}]}");
        assertRejected("{\"mVersion\":2,\"mAudioList\":[" + TRACK + ",{\"bookUrl\":\"u\",\"time\":\"abc\"}]}");
        assertRejected("{\"mVersion\":2,\"mAudioList\":[{\"bookUrl\":\"u\"}]}");
        assertRejected("{\"mVersion\":2,\"mBooksFavorite\":{\"name\":\"not a list\"}}");
    }

    @Test
    public void parsesValidDocument() throws Exception {
        BackupPOJO backup = BackupPOJO.parse("{\"mVersion\":2,"
                + "\"mBooksFavorite\":[" + BOOK + "," + BOOK + "],"
                + "\"mBooksHistory\":[" + BOOK + "],"
                + "\"mBooksSaved\":[],"
                + "\"mAudio\":[" + AUDIO + "],"
                + "\"mAudioList\":[" + TRACK + "," + TRACK + "," + TRACK + "]}");

        assertEquals(2, backup.getVersion());
        assertEquals(2, backup.getBooksFavorite().size());
        assertEquals(1, backup.getBooksHistory().size());
        assertEquals(0, backup.getBooksSaved().size());
        assertEquals(1, backup.getAudio().size());
        assertEquals(3, backup.getAudioList().size());
        assertEquals("Book", backup.getBooksFavorite().get(0).getName());
        assertEquals(5, backup.getAudio().get(0).getTime());
    }

    @Test
    public void acceptsUnversionedLegacyDocumentAndLeavesAbsentCollectionsNull() throws Exception {
        BackupPOJO backup = BackupPOJO.parse("{\"mBooksFavorite\":[" + BOOK + "],\"mAudio\":[]}");

        assertEquals(1, backup.getVersion());
        assertEquals(1, backup.getBooksFavorite().size());
        assertEquals(0, backup.getAudio().size());
        assertNull(backup.getBooksHistory());
        assertNull(backup.getBooksSaved());
        assertNull(backup.getAudioList());
    }

    private static void assertRejected(String json) {
        try {
            BackupPOJO.parse(json);
            fail("Accepted: " + json);
        } catch (BackupPOJO.InvalidBackupException expected) {
            // the typed rejection is the contract
        }
    }
}
