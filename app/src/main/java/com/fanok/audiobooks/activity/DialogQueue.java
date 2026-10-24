package com.fanok.audiobooks.activity;

import androidx.annotation.NonNull;
import java.util.ArrayDeque;

/**
 * Shows one thing at a time: a step is started only after the previous step reported {@code done}, so first-launch
 * dialogs and permission prompts never pile up on each other (UI-18). Pure Java, main-thread only.
 */
public final class DialogQueue {

    /** One decision to put to the user; it must call {@code done} exactly once, whenever it is finished. */
    public interface Step {
        void show(@NonNull Runnable done);
    }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private boolean running;

    /** Queues {@code step}; it starts now when nothing is showing. */
    public void add(@NonNull Step step) {
        steps.add(step);
        if (!running) {
            next();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void next() {
        Step step = steps.poll();
        if (step == null) {
            running = false;
            return;
        }
        running = true;
        final boolean[] finished = {false};
        step.show(() -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            next();
        });
    }
}
