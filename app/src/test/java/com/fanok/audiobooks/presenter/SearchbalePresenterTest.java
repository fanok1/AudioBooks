package com.fanok.audiobooks.presenter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchbalePresenterTest {

    @Test
    public void notFoundOnlyWhenFinishedEmptyAndNoSourceFailed() {
        assertTrue(SearchbalePresenter.isNotFound(true, 0, true));
        assertFalse("a failed source may hold the results", SearchbalePresenter.isNotFound(true, 1, true));
        assertFalse(SearchbalePresenter.isNotFound(false, 0, true));
        assertFalse(SearchbalePresenter.isNotFound(true, 0, false));
    }
}
