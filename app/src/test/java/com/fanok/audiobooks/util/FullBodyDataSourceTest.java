package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import org.junit.Test;

public class FullBodyDataSourceTest {

    /** Announces {@code length} on every open and then delivers {@code delivered} bytes, one per read. */
    private static final class Body implements DataSource {
        private final long length;
        private final int delivered;
        private int left;

        Body(long length, int delivered) {
            this.length = length;
            this.delivered = delivered;
        }

        @Override
        public long open(DataSpec dataSpec) {
            left = delivered;
            return length;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) {
            if (left == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            left--;
            return 1;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public Uri getUri() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    /**
     * Opens the source and reads {@code count} bytes, short of its end. A DataSpec needs an android.net.Uri, which the
     * JVM stubs cannot build: the source reads it only at a cut, given to {@link FullBodyDataSource#isCutAgain} here.
     */
    private static FullBodyDataSource read(FullBodyDataSource source, int count) throws IOException {
        source.open(null);
        byte[] buffer = new byte[1];
        for (int i = 0; i < count; i++) {
            assertEquals(1, source.read(buffer, 0, 1));
        }
        return source;
    }

    private static int readToEnd(FullBodyDataSource source) throws IOException {
        source.open(null);
        byte[] buffer = new byte[8];
        int total = 0;
        int read;
        while ((read = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
            total += read;
        }
        return total;
    }

    @Test
    public void aBodyOfItsAnnouncedLengthOrWithoutOneEnds() throws IOException {
        assertEquals(10, readToEnd(new FullBodyDataSource(new Body(10, 10))));
        assertEquals(4, readToEnd(new FullBodyDataSource(new Body(C.LENGTH_UNSET, 4))));
        assertEquals(0, readToEnd(new FullBodyDataSource(new Body(C.LENGTH_UNSET, 0))));
    }

    @Test
    public void theSameCutTwiceIsTheEndOfTheResource() throws IOException {
        FullBodyDataSource source = new FullBodyDataSource(new Body(10, 4));
        //cut at byte 4: a network error, asked again
        assertFalse(read(source, 4).isCutAgain("a", 0));
        //Media3 asks again from where its extractor stopped, byte 2 here: the body ends at byte 4 again
        assertTrue(read(source, 2).isCutAgain("a", 2));
    }

    @Test
    public void cutsAtDifferentBytesAreAskedAgain() throws IOException {
        FullBodyDataSource source = new FullBodyDataSource(new Body(10, 4));
        assertFalse(read(source, 4).isCutAgain("a", 0));
        assertFalse(read(source, 4).isCutAgain("a", 3));
        assertFalse(read(source, 4).isCutAgain("a", 6));
    }

    @Test
    public void anEmptyBodyWithAnAnnouncedLengthIsACut() throws IOException {
        FullBodyDataSource source = new FullBodyDataSource(new Body(10, 0));
        assertFalse(read(source, 0).isCutAgain("a", 5));
        //still nothing at byte 5: the resource ends there
        assertTrue(read(source, 0).isCutAgain("a", 5));
    }

    @Test
    public void cutsOfOtherAddressesAtTheSameByteAreAskedAgain() throws IOException {
        //HLS reads all the segments of a track through one source
        FullBodyDataSource source = new FullBodyDataSource(new Body(10, 4));
        assertFalse(read(source, 4).isCutAgain("segment1", 0));
        assertFalse(read(source, 4).isCutAgain("segment2", 0));
    }
}
