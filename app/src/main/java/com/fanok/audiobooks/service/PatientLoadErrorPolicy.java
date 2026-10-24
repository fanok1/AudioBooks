package com.fanok.audiobooks.service;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

import java.util.function.BooleanSupplier;

/**
 * Media3's load policy for the chapter being played, made patient with the network: while the user wants to listen,
 * a network gap (metro, elevator, Wi-Fi to mobile handover) or a server hiccup keeps the player buffering, with its
 * wake and Wi-Fi locks, and retrying every few seconds until the network is back, instead of failing once a few
 * retries are spent. A refused address (an HTTP 4xx other than 408/425/429) fails at once, so an expired link reaches
 * the chapter list refresh quickly. Other errors (a missing local file, a broken extractor) and every error while
 * paused fail after Media3's usual retries, so nothing keeps loading in vain. Media3's stuck-buffering detector still
 * ends a wait that makes no progress for ten minutes with a timeout error, which {@link PlaybackErrorPolicy} then
 * retries within its own cap.
 */
@OptIn(markerClass = UnstableApi.class)
final class PatientLoadErrorPolicy extends DefaultLoadErrorHandlingPolicy {

    /** True while the user wants to listen; read on the playback thread. */
    private final BooleanSupplier patient;

    PatientLoadErrorPolicy(@NonNull BooleanSupplier patient) {
        this.patient = patient;
    }

    @Override
    public int getMinimumLoadableRetryCount(int dataType) {
        //a load error reaches the player only when it is fatal, never because a retry count ran out
        return Integer.MAX_VALUE;
    }

    @Override
    public long getRetryDelayMsFor(@NonNull LoadErrorInfo loadErrorInfo) {
        boolean network = false;
        for (Throwable t = loadErrorInfo.exception; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException
                    && isPermanentHttpStatus(((HttpDataSource.InvalidResponseCodeException) t).responseCode)) {
                return C.TIME_UNSET;
            }
            if (t instanceof HttpDataSource.HttpDataSourceException) {
                network = true;
                break;
            }
        }
        if (!isPatient(network, patient.getAsBoolean(), loadErrorInfo.errorCount)) {
            return C.TIME_UNSET;
        }
        return super.getRetryDelayMsFor(loadErrorInfo);
    }

    /** Whether a load that failed {@code errorCount} times is retried once more. */
    static boolean isPatient(boolean networkError, boolean wantPlaying, int errorCount) {
        return networkError && wantPlaying || errorCount <= DEFAULT_MIN_LOADABLE_RETRY_COUNT;
    }

    /** A 4xx answer will not change by asking again, except a timeout, "too early" and "too many requests". */
    static boolean isPermanentHttpStatus(int status) {
        return status >= 400 && status <= 499 && status != 408 && status != 425 && status != 429;
    }
}
