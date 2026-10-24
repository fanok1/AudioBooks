package com.fanok.audiobooks.interface_pacatge.searchable;

import android.content.SharedPreferences;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import io.reactivex.Observable;

public interface SearchableModel {

    /**
     * Author and series chips for {@code query}: one emission per enabled source as it answers,
     * each already bounded by a timeout and reduced to an empty value on failure.
     */
    Observable<SearcheblPOJO> chips(SharedPreferences preferences, String query);
}
