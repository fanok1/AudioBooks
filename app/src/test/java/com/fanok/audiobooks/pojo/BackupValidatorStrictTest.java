package com.fanok.audiobooks.pojo;

import static org.junit.Assert.fail;

import org.junit.Test;

/** DATA-05: items with inherited defaults (empty URL) must not pass validation and wipe a table. */
public class BackupValidatorStrictTest {

    private static void assertRejected(String json) {
        try {
            BackupPOJO.parse(json);
            fail("accepted: " + json);
        } catch (BackupPOJO.InvalidBackupException expected) {
            // ok
        }
    }

    @Test
    public void emptyItemsAreRejected() {
        assertRejected("{\"mAudio\":[{}]}");
        assertRejected("{\"mAudioList\":[{}]}");
        assertRejected("{\"mBooksFavorite\":[{}]}");
        assertRejected("{\"mVersion\":2,\"mBooksSaved\":[{\"name\":\"x\"}]}");
    }
}
