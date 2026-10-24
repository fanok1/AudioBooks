package com.fanok.audiobooks;

import static org.junit.Assert.assertSame;

import io.reactivex.exceptions.UndeliverableException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class AppRxErrorHandlerTest {

    /** SRCH-16: a bug that RxJava can no longer deliver must reach the crash handler, unwrapped. */
    @Test
    public void undeliverableBugIsForwardedUnwrappedToUncaughtHandler() {
        Thread thread = Thread.currentThread();
        AtomicReference<Throwable> seen = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((t, e) -> seen.set(e));
        try {
            IllegalStateException bug = new IllegalStateException("bug");
            App.handleRxError(new UndeliverableException(bug));
            assertSame(bug, seen.get());

            NullPointerException npe = new NullPointerException("npe");
            App.handleRxError(npe);
            assertSame(npe, seen.get());
        } finally {
            thread.setUncaughtExceptionHandler(null);
        }
    }
}
