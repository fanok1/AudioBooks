package com.fanok.audiobooks.interface_pacatge.searchable;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.GenrePOJO;

import org.junit.Test;

import java.util.ArrayList;

public class SearchableModelAbstractTest {

    @Test
    public void getSicretKey() {
        SearchableModelAbstract searchableModelAbstract = new SearchableModelAbstract() {
            @Override
            public ArrayList<GenrePOJO> loadBooksList(@NonNull String url, @NonNull String qery) {
                return null;
            }
        };
        searchableModelAbstract.setSikretKey();
        System.out.println(searchableModelAbstract.getSicretKey());
        searchableModelAbstract.setSikretKey();
        System.out.println(searchableModelAbstract.getSicretKey());
    }
}