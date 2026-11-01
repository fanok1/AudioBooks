package com.fanok.audiobooks.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.fragment.SourcesDialogFragment.Rows;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The rows of the sources dialog: moves on one side of the divider, across it, and at its limits. */
public class SourcesDialogFragmentTest {

    private static final Provider A = Provider.values()[0];
    private static final Provider B = Provider.values()[1];
    private static final Provider C = Provider.values()[2];
    private static final Provider D = Provider.values()[3];

    /** {@code [A, B, | C, D]}: two active, two inactive. */
    private static List<Object> rows() {
        return Rows.of(Arrays.asList(A, B, C, D), 2);
    }

    @Test
    public void dividerIsTheActiveCount() {
        List<Object> rows = rows();
        assertEquals(2, Rows.divider(rows));
        assertEquals(Arrays.asList(A, B), Rows.active(rows));
        assertEquals(Arrays.asList(A, B, C, D), Rows.providers(rows));
    }

    @Test
    public void limitIsTheSmallerOfMaxActiveAndTotal() {
        assertEquals(5, Rows.limit(5));
        assertEquals(Sources.MAX_ACTIVE, Rows.limit(Sources.MAX_ACTIVE + 7));
    }

    @Test
    public void moveOnOneSideKeepsTheCount() {
        List<Object> rows = rows();
        assertTrue(Rows.canMove(rows, 0, 1));
        Rows.move(rows, 0, 1);
        assertEquals(Arrays.asList(B, A, C, D), Rows.providers(rows));
        assertEquals(2, Rows.divider(rows));
        assertTrue(Rows.canMove(rows, 4, 3));
        Rows.move(rows, 4, 3);
        assertEquals(Arrays.asList(B, A, D, C), Rows.providers(rows));
        assertEquals(2, Rows.divider(rows));
    }

    @Test
    public void deactivateLandsJustBelowTheDivider() {
        List<Object> rows = rows();
        assertTrue(Rows.canMove(rows, 0, 2));
        Rows.move(rows, 0, 2);
        assertEquals(Arrays.asList(B, A, C, D), Rows.providers(rows));
        assertEquals(1, Rows.divider(rows));
        assertEquals(Arrays.asList(B), Rows.active(rows));
    }

    @Test
    public void activateLandsJustAboveTheDivider() {
        List<Object> rows = rows();
        assertTrue(Rows.canMove(rows, 4, 2));
        Rows.move(rows, 4, 2);
        assertEquals(Arrays.asList(A, B, D, C), Rows.providers(rows));
        assertEquals(3, Rows.divider(rows));
    }

    @Test
    public void dragFarAcrossTheDividerChangesTheCountByOne() {
        List<Object> rows = rows();
        assertTrue(Rows.canMove(rows, 0, 4));
        Rows.move(rows, 0, 4);
        assertEquals(Arrays.asList(B, C, D, A), Rows.providers(rows));
        assertEquals(1, Rows.divider(rows));
    }

    @Test
    public void theLastActiveSourceStays() {
        List<Object> rows = Rows.of(Arrays.asList(A, B, C), 1);
        assertFalse(Rows.canMove(rows, 0, 1));
        assertFalse(Rows.canMove(rows, 0, 3));
        assertTrue(Rows.canMove(rows, 2, 1)); // activating is still allowed
    }

    @Test
    public void noMoreThanTheLimitBecomesActive() {
        Provider[] all = Provider.values();
        List<Object> rows = Rows.of(Arrays.asList(all), all.length - 1);
        int divider = Rows.divider(rows);
        boolean room = all.length <= Sources.MAX_ACTIVE;
        assertEquals(room, Rows.canMove(rows, divider + 1, divider));
        assertEquals(room, Rows.canMove(rows, divider + 1, 0));
    }

    @Test
    public void stepperFormOfTheMove() {
        List<Object> rows = rows();
        // "+": the first inactive source crosses up
        assertTrue(Rows.canMove(rows, 3, 2));
        Rows.move(rows, 3, 2);
        assertEquals(3, Rows.divider(rows));
        assertEquals(Arrays.asList(A, B, C), Rows.active(rows));
        // "−": the last active source crosses down
        assertTrue(Rows.canMove(rows, 2, 3));
        Rows.move(rows, 2, 3);
        assertEquals(2, Rows.divider(rows));
        assertEquals(Arrays.asList(A, B, C, D), Rows.providers(rows));
    }

    @Test
    public void theDividerAndOutOfRangeIndexesNeverMove() {
        List<Object> rows = rows();
        assertFalse(Rows.canMove(rows, 2, 1));
        assertFalse(Rows.canMove(rows, -1, 1));
        assertFalse(Rows.canMove(rows, 0, 5));
    }
}
