package com.fanok.audiobooks.util;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;
import androidx.media3.datasource.TransferListener;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An HTTP source whose body must reach the length the response announced: one that ends sooner, an empty one too, is
 * a network error, never the end of the media. OkHttp fails a body cut short over HTTP/1.1, but over HTTP/2 a stream
 * the server ends early reads as a plain end; a long chapter, read at playback speed, keeps its stream waiting on flow
 * control for minutes. Taken for the end of the media, the cut ended the chapter's audio there and the player ran
 * silently to the chapter's duration. As an error, Media3 asks again from where its extractor stopped. A body cut at
 * the very byte the previous cut of the same address ended at is the resource's real end: asking again would get the
 * same, forever. Bodies without an announced length pass as they are; caches and files are not HTTP and keep theirs.
 */
@OptIn(markerClass = UnstableApi.class)
final class FullBodyDataSource implements DataSource {

    private static final String TAG = "FullBodyDataSource";

    @NonNull
    static DataSource.Factory factory(@NonNull DataSource.Factory upstream) {
        return () -> new FullBodyDataSource(upstream.createDataSource());
    }

    private final DataSource upstream;
    @Nullable
    private DataSpec dataSpec;
    /** What is left of the announced length, {@link C#LENGTH_UNSET} when none was announced. */
    private long bytesLeft;
    private long bytesRead;
    /**
     * The previous cut: its address and the offset in the resource where its body ended. A load asks the same source
     * again after an error; HLS asks one source for all the segments of a track, hence the address.
     */
    @Nullable
    private Object cutUri;
    private long cutAt = C.POSITION_UNSET;

    FullBodyDataSource(@NonNull DataSource upstream) {
        this.upstream = upstream;
    }

    /**
     * Whether the body of {@code uri} opened at {@code position}, cut short now, ended at the offset where the previous
     * cut ended: then that is where the resource ends. Remembers the cut.
     */
    @VisibleForTesting
    boolean isCutAgain(@Nullable Object uri, long position) {
        long endOffset = position + bytesRead;
        boolean again = endOffset == cutAt && Objects.equals(uri, cutUri);
        cutUri = uri;
        cutAt = endOffset;
        return again;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws IOException {
        this.dataSpec = dataSpec;
        bytesRead = 0;
        bytesLeft = upstream.open(dataSpec);
        return bytesLeft;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        int read = upstream.read(buffer, offset, length);
        if (read != C.RESULT_END_OF_INPUT) {
            bytesRead += read;
            if (bytesLeft != C.LENGTH_UNSET) {
                bytesLeft -= read;
            }
            return read;
        }
        if (bytesLeft > 0) {
            //short of the announced length (C.LENGTH_UNSET when none was announced), an empty body too
            DataSpec spec = Objects.requireNonNull(dataSpec);
            if (!isCutAgain(spec.uri, spec.position)) {
                throw new HttpDataSourceException(new EOFException("the body ended " + bytesLeft + " bytes short"),
                        spec, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        HttpDataSourceException.TYPE_READ);
            }
            //the host only: chapter addresses carry signatures
            Log.w(TAG, "a body of " + spec.uri.getHost() + " ended " + bytesLeft
                    + " bytes short at the same byte again: taken for its end");
        }
        return read;
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        upstream.close();
    }
}
