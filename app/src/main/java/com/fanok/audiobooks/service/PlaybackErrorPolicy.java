package com.fanok.audiobooks.service;

import androidx.annotation.NonNull;

/**
 * What the player does after a playback error, kept free of Android types so it is unit-testable.
 * One instance tracks the retry budget of the chapter being played; {@link #reset()} restores it once the chapter
 * plays again, another chapter is loaded or the user presses Play. Automatic retries never reset it.
 */
public final class PlaybackErrorPolicy {

    /**
     * Delays of the re-prepare attempts after transient errors: two quick ones, then a slow backoff whose last
     * step repeats until {@link #TRANSIENT_GIVE_UP_MS} have passed since the first error of the streak.
     */
    static final long[] BACKOFF_MS = {2_000, 5_000, 10_000, 30_000, 60_000};

    /** How long a streak of transient errors is retried before the player gives up. */
    static final long TRANSIENT_GIVE_UP_MS = 30 * 60_000L;

    /** The delay of the one retry of an error of no known kind. */
    static final long OTHER_RETRY_MS = 5_000;

    // androidx.media3.common.PlaybackException error codes
    static final int ERROR_CODE_TIMEOUT = 1003;
    static final int ERROR_CODE_IO_UNSPECIFIED = 2000;
    static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001;
    static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002;
    static final int ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 2003;
    static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 2004;
    static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 2008;

    public enum Kind {
        /** Network trouble that may pass: HTTP 5xx/408/425/429, timeouts, failed connections. */
        TRANSIENT,
        /** The address expired or is refused (HTTP 401/403/410): a fresh chapter list may fix it. */
        AUTH,
        /** The chapter itself cannot be played (parsing, decoding, unsupported format): Next skips it. */
        CONTENT,
        /** Anything else (missing file, 404, no permission ...): retried once, then reported; Play retries. */
        OTHER
    }

    public enum Action {
        /** Re-prepare the chapter at the checkpoint position after {@link Decision#delayMs}. */
        RETRY,
        /** Refresh the chapter list once in the background, then re-prepare. */
        REFRESH,
        /** Stop trying: give up the intent and publish the error; Play retries and Next skips. */
        FAIL
    }

    public static final class Decision {
        @NonNull
        public final Action action;
        @NonNull
        public final Kind kind;
        public final long delayMs;

        Decision(@NonNull Action action, @NonNull Kind kind, long delayMs) {
            this.action = action;
            this.kind = kind;
            this.delayMs = delayMs;
        }
    }

    private int retries;

    private boolean otherRetried;

    private boolean refreshed;

    /** When the first error since {@link #reset()} came, or -1. */
    private long streakStartMs = -1;

    /**
     * @param errorCode  {@code PlaybackException.errorCode}
     * @param httpStatus the response code of a bad HTTP status, or -1
     */
    @NonNull
    public static Kind classify(int errorCode, int httpStatus) {
        switch (errorCode) {
            case ERROR_CODE_TIMEOUT:
            case ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return Kind.TRANSIENT;
            case ERROR_CODE_IO_UNSPECIFIED:
                //not a data source error (a clip past the end, an extractor crash): the loader retried it already
                return Kind.OTHER;
            case ERROR_CODE_IO_BAD_HTTP_STATUS:
                if (httpStatus >= 500 && httpStatus <= 599
                        || httpStatus == 408 || httpStatus == 425 || httpStatus == 429) {
                    return Kind.TRANSIENT;
                }
                if (httpStatus == 401 || httpStatus == 403 || httpStatus == 410) {
                    return Kind.AUTH;
                }
                return Kind.OTHER;
            case ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
                return Kind.CONTENT;
            case ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE:
                //the server no longer has the range: the same request cannot succeed soon
                return Kind.OTHER;
            default:
                // 3xxx parsing, 4xxx decoding
                return errorCode >= 3000 && errorCode < 5000 ? Kind.CONTENT : Kind.OTHER;
        }
    }

    /**
     * Spends the budget for one error of the current chapter and says what to do next.
     *
     * @param nowMs a monotonic clock that counts sleep ({@code SystemClock.elapsedRealtime()})
     */
    @NonNull
    public Decision onError(int errorCode, int httpStatus, long nowMs) {
        startStreak(nowMs);
        Kind kind = classify(errorCode, httpStatus);
        switch (kind) {
            case TRANSIENT:
                return transientRetry(nowMs);
            case AUTH:
                //stays spent until reset(): a fresh address that is refused too cannot loop
                if (!refreshed) {
                    refreshed = true;
                    return new Decision(Action.REFRESH, kind, 0);
                }
                return new Decision(Action.FAIL, kind, 0);
            case OTHER:
                if (!otherRetried) {
                    otherRetried = true;
                    return new Decision(Action.RETRY, kind, OTHER_RETRY_MS);
                }
                return new Decision(Action.FAIL, kind, 0);
            default:
                return new Decision(Action.FAIL, kind, 0);
        }
    }

    /**
     * The chapter list refresh after a refused address could not be fetched: retried like network trouble, and
     * the next refused address may refresh once more.
     */
    @NonNull
    public Decision onRefreshFailed(long nowMs) {
        startStreak(nowMs);
        refreshed = false;
        return transientRetry(nowMs);
    }

    /**
     * Whether a failed chapter list fetch is network trouble worth retrying: an I/O error, but not an HTTP answer
     * that will not change (a 4xx other than 408/425/429). A challenge page or a page that no longer parses is not.
     */
    public static boolean isRetryableRefreshError(boolean ioException, int httpStatus) {
        return ioException && !PatientLoadErrorPolicy.isPermanentHttpStatus(httpStatus);
    }

    /** The streak of errors began {@link #TRANSIENT_GIVE_UP_MS} or more ago: waiting any longer is giving up. */
    public boolean isExpired(long nowMs) {
        return streakStartMs >= 0 && nowMs - streakStartMs >= TRANSIENT_GIVE_UP_MS;
    }

    /** The chapter played, or another one is loaded, or the user asked to retry: a full budget again. */
    public void reset() {
        retries = 0;
        otherRetried = false;
        refreshed = false;
        streakStartMs = -1;
    }

    private void startStreak(long nowMs) {
        if (streakStartMs < 0) {
            streakStartMs = nowMs;
        }
    }

    @NonNull
    private Decision transientRetry(long nowMs) {
        if (nowMs - streakStartMs >= TRANSIENT_GIVE_UP_MS) {
            return new Decision(Action.FAIL, Kind.TRANSIENT, 0);
        }
        long delay = BACKOFF_MS[Math.min(retries, BACKOFF_MS.length - 1)];
        if (retries < BACKOFF_MS.length) {
            retries++;
        }
        return new Decision(Action.RETRY, Kind.TRANSIENT, delay);
    }

    /** A clip whose end is not after its start would fail or play nothing; negative values mean "no clipping". */
    public static boolean isValidClip(int startSeconds, int finishSeconds) {
        return startSeconds < 0 || finishSeconds < 0 || finishSeconds > startSeconds;
    }
}
