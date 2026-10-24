package com.fanok.audiobooks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NaturalOrderTest {

    @Test
    public void numbersSortByValue() {
        assertEquals(Arrays.asList("1", "2", "10"), sorted("10", "2", "1"));
        assertEquals(Arrays.asList("Глава 2", "Глава 10"), sorted("Глава 10", "Глава 2"));
        assertEquals(Arrays.asList("track9.mp3", "track10.mp3", "track100.mp3"),
                sorted("track100.mp3", "track10.mp3", "track9.mp3"));
    }

    @Test
    public void leadingZerosAreEqualNumbersTieBrokenLexically() {
        assertTrue(StorageNames.naturalCompare("01", "1") < 0);
        assertTrue(StorageNames.naturalCompare("1", "01") > 0);
        assertTrue(StorageNames.naturalCompare("01", "2") < 0);
        assertTrue(StorageNames.naturalCompare("010", "9") > 0);
        assertEquals(Arrays.asList("01", "1", "2", "10"), sorted("10", "2", "1", "01"));
    }

    @Test
    public void textIsCaseInsensitiveAndPrefixesComeFirst() {
        assertTrue(StorageNames.naturalCompare("a2", "A10") < 0);
        assertTrue(StorageNames.naturalCompare("Глава", "Глава 1") < 0);
        assertEquals(0, StorageNames.naturalCompare("Глава 1", "Глава 1"));
    }

    @Test
    public void hugeNumbersDoNotOverflow() {
        assertTrue(StorageNames.naturalCompare("99999999999999999999", "100000000000000000000") < 0);
    }

    @Test
    public void orderIsAntisymmetric() {
        List<String> names = Arrays.asList("", "0", "00", "1", "01", "1a", "a1", "A1", "Глава 2", "глава 10", "x");
        for (String a : names) {
            for (String b : names) {
                assertEquals(a + " vs " + b, Integer.signum(StorageNames.naturalCompare(a, b)),
                        -Integer.signum(StorageNames.naturalCompare(b, a)));
            }
        }
    }

    private static List<String> sorted(String... names) {
        List<String> list = new ArrayList<>(Arrays.asList(names));
        Collections.sort(list, StorageNames.NATURAL_ORDER);
        return list;
    }
}
