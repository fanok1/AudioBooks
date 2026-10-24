package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Action;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Decision;
import com.fanok.audiobooks.service.PlaybackErrorPolicy.Kind;
import org.junit.Test;

public class PlaybackErrorPolicyTest {

    private static final int BAD_STATUS = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;
    private static final long CAP = PlaybackErrorPolicy.TRANSIENT_GIVE_UP_MS;

    @Test
    public void errorCodes_matchMedia3() {
        assertEquals(PlaybackException.ERROR_CODE_TIMEOUT, PlaybackErrorPolicy.ERROR_CODE_TIMEOUT);
        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackErrorPolicy.ERROR_CODE_IO_UNSPECIFIED);
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackErrorPolicy.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackErrorPolicy.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);
        assertEquals(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackErrorPolicy.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE);
        assertEquals(BAD_STATUS, PlaybackErrorPolicy.ERROR_CODE_IO_BAD_HTTP_STATUS);
        assertEquals(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                PlaybackErrorPolicy.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }

    @Test
    public void classify_networkTroubleIsTransient() {
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 503));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 500));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 408));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 425));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(BAD_STATUS, 429));
        assertEquals(Kind.TRANSIENT,
                PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, -1));
        assertEquals(Kind.TRANSIENT,
                PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, -1));
        assertEquals(Kind.TRANSIENT, PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_TIMEOUT, -1));
    }

    @Test
    public void classify_refusedAddressIsAuth_missingFileIsOther() {
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 401));
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 403));
        assertEquals(Kind.AUTH, PlaybackErrorPolicy.classify(BAD_STATUS, 410));
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(BAD_STATUS, 404));
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(BAD_STATUS, 400));
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, -1));
        assertEquals(Kind.OTHER,
                PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, -1));
        // not a data source error: a clip past the end of the stream, an extractor crash
        assertEquals(Kind.OTHER, PlaybackErrorPolicy.classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, -1));
    }

    @Test
    public void onlyNetworkTroubleInARefreshIsRetried() {
        assertTrue(PlaybackErrorPolicy.isRetryableRefreshError(true, -1));
        assertTrue(PlaybackErrorPolicy.isRetryableRefreshError(true, 503));
        assertTrue(PlaybackErrorPolicy.isRetryableRefreshError(true, 429));
        assertFalse(PlaybackErrorPolicy.isRetryableRefreshError(true, 404));
        assertFalse(PlaybackErrorPolicy.isRetryableRefreshError(true, 403));
        // a challenge page (CookesExeption) or a page that no longer parses
        assertFalse(PlaybackErrorPolicy.isRetryableRefreshError(false, -1));
    }

    @Test
    public void streakExpiresAtTheCap() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        assertFalse(policy.isExpired(0));
        policy.onError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, -1, 1_000);
        assertFalse(policy.isExpired(1_000 + PlaybackErrorPolicy.TRANSIENT_GIVE_UP_MS - 1));
        assertTrue(policy.isExpired(1_000 + PlaybackErrorPolicy.TRANSIENT_GIVE_UP_MS));
        policy.reset();
        assertFalse(policy.isExpired(Long.MAX_VALUE));
    }

    @Test
    public void transient_quickRetriesThenSlowBackoffUntilTheCap() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        long now = 1_000;
        long[] expected = {2_000, 5_000, 10_000, 30_000, 60_000, 60_000, 60_000};
        for (long delay : expected) {
            Decision decision = policy.onError(BAD_STATUS, 503, now);
            assertEquals(Action.RETRY, decision.action);
            assertEquals(Kind.TRANSIENT, decision.kind);
            assertEquals(delay, decision.delayMs);
            now += delay;
        }
        //the slow step repeats until the streak is 30 minutes old
        Decision last = policy.onError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, -1,
                1_000 + CAP - 1);
        assertEquals(Action.RETRY, last.action);
        assertEquals(60_000, last.delayMs);
        Decision gaveUp = policy.onError(BAD_STATUS, 503, 1_000 + CAP);
        assertEquals(Action.FAIL, gaveUp.action);
        assertEquals(Kind.TRANSIENT, gaveUp.kind);
    }

    @Test
    public void reset_startsANewStreakWithAFullBudget() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        policy.onError(BAD_STATUS, 503, 0);
        policy.onError(BAD_STATUS, 503, 2_000);
        assertEquals(Action.FAIL, policy.onError(BAD_STATUS, 503, CAP).action);

        //Play after the error, a READY chapter or a new chapter: the streak starts at the next error
        policy.reset();
        Decision first = policy.onError(BAD_STATUS, 503, 10 * CAP);
        assertEquals(Action.RETRY, first.action);
        assertEquals(2_000, first.delayMs);
        assertEquals(Action.RETRY, policy.onError(BAD_STATUS, 503, 11 * CAP - 1).action);
        assertEquals(Action.FAIL, policy.onError(BAD_STATUS, 503, 11 * CAP).action);
    }

    @Test
    public void other_oneRetryThenGiveUp() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        Decision first = policy.onError(BAD_STATUS, 404, 0);
        assertEquals(Action.RETRY, first.action);
        assertEquals(Kind.OTHER, first.kind);
        assertEquals(PlaybackErrorPolicy.OTHER_RETRY_MS, first.delayMs);
        Decision second = policy.onError(BAD_STATUS, 404, PlaybackErrorPolicy.OTHER_RETRY_MS);
        assertEquals(Action.FAIL, second.action);
        assertEquals(Kind.OTHER, second.kind);

        policy.reset();
        assertEquals(Action.RETRY, policy.onError(BAD_STATUS, 404, 0).action);
    }

    @Test
    public void auth_refreshesTheChapterListOnceThenGivesUp() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 403, 0).action);
        //network trouble in between does not give the refused address a second refresh
        assertEquals(Action.RETRY, policy.onError(BAD_STATUS, 503, 1_000).action);
        Decision again = policy.onError(BAD_STATUS, 403, 5_000);
        assertEquals(Action.FAIL, again.action);
        assertEquals(Kind.AUTH, again.kind);

        policy.reset();
        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 410, 0).action);
    }

    @Test
    public void refreshFailure_retriesLikeNetworkTroubleAndAllowsOneMoreRefresh() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 403, 0).action);
        Decision retry = policy.onRefreshFailed(1_000);
        assertEquals(Action.RETRY, retry.action);
        assertEquals(Kind.TRANSIENT, retry.kind);
        assertEquals(2_000, retry.delayMs);

        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 403, 3_000).action);
        Decision second = policy.onRefreshFailed(4_000);
        assertEquals(Action.RETRY, second.action);
        assertEquals(5_000, second.delayMs);

        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 403, 9_000).action);
        //the fresh list came, and its address is refused too
        assertEquals(Action.FAIL, policy.onError(BAD_STATUS, 403, 10_000).action);
    }

    @Test
    public void refreshFailure_givesUpAtTheCap() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        assertEquals(Action.REFRESH, policy.onError(BAD_STATUS, 401, 0).action);
        Decision gaveUp = policy.onRefreshFailed(CAP);
        assertEquals(Action.FAIL, gaveUp.action);
        assertEquals(Kind.TRANSIENT, gaveUp.kind);
    }

    @Test
    public void decoderAndParserErrors_failAtOnceAsSkippableChapter() {
        PlaybackErrorPolicy policy = new PlaybackErrorPolicy();
        for (int code : new int[]{PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE}) {
            Decision decision = policy.onError(code, -1, 0);
            assertEquals(Action.FAIL, decision.action);
            assertEquals(Kind.CONTENT, decision.kind);
        }
    }

    @Test
    public void clip_endMustFollowStart() {
        assertTrue(PlaybackErrorPolicy.isValidClip(-1, -1));
        assertTrue(PlaybackErrorPolicy.isValidClip(10, -1));
        assertTrue(PlaybackErrorPolicy.isValidClip(10, 20));
        assertFalse(PlaybackErrorPolicy.isValidClip(20, 20));
        assertFalse(PlaybackErrorPolicy.isValidClip(30, 20));
    }
}
