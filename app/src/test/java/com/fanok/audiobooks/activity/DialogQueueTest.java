package com.fanok.audiobooks.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DialogQueueTest {

    @Test
    public void stepsRunOneAtATimeInOrder() {
        DialogQueue queue = new DialogQueue();
        List<String> log = new ArrayList<>();
        List<Runnable> pending = new ArrayList<>();

        queue.add(done -> {
            log.add("a");
            pending.add(done);
        });
        queue.add(done -> {
            log.add("b");
            pending.add(done);
        });
        assertEquals(Arrays.asList("a"), log);
        assertTrue(queue.isRunning());

        pending.get(0).run();
        assertEquals(Arrays.asList("a", "b"), log);

        queue.add(done -> {
            log.add("c");
            done.run();
        });
        assertEquals(Arrays.asList("a", "b"), log);

        pending.get(1).run();
        assertEquals(Arrays.asList("a", "b", "c"), log);
        assertFalse(queue.isRunning());
    }

    @Test
    public void aDoneCalledTwiceAdvancesOnce() {
        DialogQueue queue = new DialogQueue();
        List<String> log = new ArrayList<>();
        List<Runnable> pending = new ArrayList<>();
        queue.add(done -> {
            log.add("a");
            pending.add(done);
        });
        queue.add(done -> {
            log.add("b");
            pending.add(done);
        });
        queue.add(done -> {
            log.add("c");
            pending.add(done);
        });
        pending.get(0).run();
        pending.get(0).run();
        assertEquals(Arrays.asList("a", "b"), log);
    }
}
