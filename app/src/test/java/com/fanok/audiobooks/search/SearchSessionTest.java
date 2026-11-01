package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jsoup.HttpStatusException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.SingleSubject;

public class SearchSessionTest {

    private TestScheduler scheduler;
    private final List<List<String>> published = new ArrayList<>();
    private final List<String> finished = new ArrayList<>();
    private int finishedFailures = -1;

    private final SearchSession.Listener<String> listener = new SearchSession.Listener<String>() {
        @Override
        public void onItems(List<String> items) {
            published.add(new ArrayList<>(items));
        }

        @Override
        public void onFinished(List<String> items, int failedSources) {
            finished.add(String.join(",", items));
            finishedFailures = failedSources;
        }
    };

    @Before
    public void setUp() {
        scheduler = new TestScheduler();
    }

    private SearchSession<String> session(List<SearchSession.Source<String>> sources) {
        return new SearchSession<>(sources, s -> s, scheduler, scheduler, 10, listener);
    }

    /** A source answering {@code items} after {@code delaySeconds}; a null page means "never answers". */
    private static SearchSession.Source<String> source(String name, long delaySeconds, ProviderPage<String> page,
            TestScheduler scheduler) {
        return new SearchSession.Source<String>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                if (page == null) {
                    return Single.never();
                }
                return Single.timer(delaySeconds, TimeUnit.SECONDS, scheduler).map(t -> page);
            }
        };
    }

    private static ProviderPage<String> page(boolean hasMore, String... items) {
        return ProviderPage.of(Arrays.asList(items), hasMore);
    }

    @Test
    public void atMostMaxParallelSourcesLoadAtOnceAndAQueuedOneIsNotTimedOutWhileWaiting() {
        int count = SearchSession.MAX_PARALLEL + 2;
        List<SearchSession.Source<String>> sources = new ArrayList<>();
        int[] running = {0};
        int[] peak = {0};
        for (int i = 0; i < count; i++) {
            String name = "S" + i;
            sources.add(new SearchSession.Source<String>() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public Single<ProviderPage<String>> load(String query, int pageNo) {
                    return Single.timer(8, TimeUnit.SECONDS, scheduler)
                            .map(t -> page(false, name))
                            .doOnSubscribe(d -> peak[0] = Math.max(peak[0], ++running[0]))
                            .doFinally(() -> running[0]--);
                }
            });
        }
        SearchSession<String> s = session(sources);
        s.search("x");
        scheduler.advanceTimeBy(8, TimeUnit.SECONDS);
        assertEquals(SearchSession.MAX_PARALLEL, peak[0]);
        // the last two start at 8 s and answer at 16 s, inside their own 10 s
        scheduler.advanceTimeBy(8, TimeUnit.SECONDS);
        assertEquals(1, finished.size());
        assertEquals(0, finishedFailures);
        assertEquals(count, finished.get(0).split(",").length);
    }

    @Test
    public void resultsMergeInOrderOfArrivalAndRenderProgressively() {
        SearchSession<String> s = session(Arrays.asList(
                source("A", 5, page(false, "a1", "a2"), scheduler),
                source("B", 1, page(false, "b1"), scheduler)));
        s.search("1984");
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList(Arrays.asList("b1")), published);
        assertTrue(finished.isEmpty());
        scheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        assertEquals(Arrays.asList("b1", "a1", "a2"), published.get(1));
        assertEquals(Collections.singletonList("b1,a1,a2"), finished);
        assertEquals(0, finishedFailures);
        assertFalse(s.isRunning());
    }

    @Test
    public void allSourcesDisabledFinishesImmediatelyEmpty() {
        SearchSession<String> s = session(Collections.emptyList());
        s.search("1984");
        assertEquals(Collections.singletonList(""), finished);
        assertEquals(0, finishedFailures);
        assertTrue(published.isEmpty());
        assertFalse(s.hasMore());
    }

    @Test
    public void forbiddenSourceIsBlockedAndOthersStillShow() {
        SearchSession.Source<String> blocked = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "B";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                return Single.error(new HttpStatusException("forbidden", 403, "https://x/"));
            }
        };
        SearchSession<String> s = session(Arrays.asList(source("A", 1, page(false, "a1"), scheduler), blocked));
        s.search("1984");
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList("a1"), finished);
        assertEquals(1, finishedFailures);
        assertEquals(ProviderPage.Status.BLOCKED, s.statuses().get("B"));
        assertEquals(ProviderPage.Status.OK, s.statuses().get("A"));
        assertEquals(1, s.failedCount());
    }

    @Test
    public void silentSourceTimesOutAtTenSecondsAndTheSessionCompletes() {
        SearchSession<String> s = session(Arrays.asList(
                source("A", 1, page(false, "a1"), scheduler),
                source("Z", 0, null, scheduler)));
        s.search("1984");
        scheduler.advanceTimeBy(9, TimeUnit.SECONDS);
        assertTrue(finished.isEmpty());
        assertTrue(s.isRunning());
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList("a1"), finished);
        assertEquals(ProviderPage.Status.TIMEOUT, s.statuses().get("Z"));
        assertEquals(1, finishedFailures);
    }

    /** A slow site's own limit (its declared page timeout) replaces the session's shorter one. */
    @Test
    public void slowSourceGetsItsOwnLongerLimit() {
        SearchSession.Source<String> late = source("S", 15, page(false, "s1"), scheduler);
        SearchSession.Source<String> slow = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return late.name();
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                return late.load(query, pageNo);
            }

            @Override
            public long timeoutSeconds() {
                return 35;
            }
        };
        SearchSession<String> s = session(Arrays.asList(source("A", 1, page(false, "a1"), scheduler), slow));
        s.search("маг поневоле");
        scheduler.advanceTimeBy(14, TimeUnit.SECONDS);
        assertTrue(s.isRunning());
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList("a1,s1"), finished);
        assertEquals(ProviderPage.Status.OK, s.statuses().get("S"));
        assertEquals(0, finishedFailures);
    }

    @Test
    public void emptyEverywhereFinishesEmptyWithoutSentinelsAndRetriesOnlyOnce() {
        final int[] calls = {0};
        SearchSession.Source<String> empty = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "E";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                calls[0]++;
                return Single.just(ProviderPage.<String>empty());
            }
        };
        SearchSession<String> s = session(Collections.singletonList(empty));
        s.search("abc");
        scheduler.triggerActions();
        assertEquals(Collections.singletonList(""), finished);
        assertEquals(0, finishedFailures);
        assertTrue(published.isEmpty());
        assertEquals("one pass plus one case-variant retry", 2, calls[0]);
        assertFalse(s.hasMore());
        assertFalse(s.loadNext());
    }

    @Test
    public void lateResultsOfASupersededQueryAreIgnored() {
        SearchSession<String> s = session(Collections.singletonList(source("A", 5, page(false, "old"), scheduler)));
        s.search("first");
        scheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        s.search("second");
        scheduler.advanceTimeBy(3, TimeUnit.SECONDS); // the first run would have answered now
        assertTrue(published.isEmpty());
        assertTrue(finished.isEmpty());
        scheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList(Arrays.asList("old")), published);
        assertEquals(1, finished.size());
    }

    @Test
    public void nextPageContinuesOnlySourcesWithMoreAndDeduplicates() {
        final List<String> requested = new ArrayList<>();
        SearchSession.Source<String> paged = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "P";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                requested.add("P" + pageNo);
                return Single.just(pageNo == 1 ? page(true, "x", "y") : page(false, "y", "z"));
            }
        };
        SearchSession.Source<String> single = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "S";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                requested.add("S" + pageNo);
                return Single.just(page(false, "s"));
            }
        };
        SearchSession<String> s = session(Arrays.asList(paged, single));
        s.search("1984");
        scheduler.triggerActions();
        assertTrue(s.hasMore());
        assertTrue(s.loadNext());
        scheduler.triggerActions();
        assertEquals(Arrays.asList("P1", "S1", "P2"), requested);
        assertEquals("x,y,s,z", finished.get(1));
        assertFalse(s.hasMore());
    }

    @Test
    public void cancelMidRunLeavesNothingPendingAndTheNextRequestStartsAgain() {
        SearchSession<String> s = session(Collections.singletonList(source("A", 5, page(true, "a1"), scheduler)));
        s.search("1984");
        scheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        s.dispose();
        assertFalse(s.isRunning());
        scheduler.advanceTimeBy(5, TimeUnit.SECONDS);
        assertTrue(published.isEmpty());
        assertTrue(finished.isEmpty());
        assertTrue("the unanswered source keeps its cursor", s.hasMore());
        assertTrue(s.loadNext());
        scheduler.advanceTimeBy(5, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList(Arrays.asList("a1")), published);
        assertEquals(Collections.singletonList("a1"), finished);
    }

    @Test
    public void aLateAnswerOfTheSupersededRunAfterTheNewRunFinishedIsIgnored() {
        final SingleSubject<ProviderPage<String>> late = SingleSubject.create();
        SearchSession.Source<String> src = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "A";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                return query.equals("first") ? late
                        : Single.timer(1, TimeUnit.SECONDS, scheduler).map(t -> page(false, "new"));
            }
        };
        SearchSession<String> s = session(Collections.singletonList(src));
        s.search("first");
        scheduler.triggerActions();
        s.search("second");
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList(Arrays.asList("new")), published);
        assertEquals(Collections.singletonList("new"), finished);
        late.onSuccess(page(false, "old"));
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList(Arrays.asList("new")), published);
        assertEquals(Collections.singletonList("new"), finished);
        assertEquals(Arrays.asList("new"), s.items());
        assertFalse(s.isRunning());
    }

    @Test
    public void aSourceRepeatingItsPageIsRetiredAfterTheSecondFetch() {
        final int[] calls = {0};
        SearchSession.Source<String> repeating = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "R";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                calls[0]++;
                return Single.just(page(true, "a", "b"));
            }
        };
        SearchSession<String> s = session(Collections.singletonList(repeating));
        s.search("1984");
        scheduler.triggerActions();
        assertTrue(s.hasMore());
        assertTrue(s.loadNext());
        scheduler.triggerActions();
        assertEquals(2, calls[0]);
        assertFalse(s.hasMore());
        assertFalse(s.loadNext());
        assertEquals("a,b", finished.get(1));
    }

    @Test
    public void aPageWhoseCardsWereAllFilteredKeepsItsSourceGoing() {
        SearchSession.Source<String> filtered = new SearchSession.Source<String>() {
            @Override
            public String name() {
                return "F";
            }

            @Override
            public Single<ProviderPage<String>> load(String query, int pageNo) {
                return Single.just(pageNo == 1 ? ProviderPage.<String>of(Collections.<String>emptyList(), true, 5)
                        : page(false, "f2"));
            }
        };
        SearchSession<String> s = session(Collections.singletonList(filtered));
        s.search("1984");
        scheduler.triggerActions();
        assertEquals(ProviderPage.Status.OK, s.statuses().get("F"));
        assertTrue(s.loadNext());
        scheduler.triggerActions();
        assertEquals("f2", finished.get(1));
    }
}
