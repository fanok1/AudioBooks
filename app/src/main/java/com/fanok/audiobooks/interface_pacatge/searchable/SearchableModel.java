package com.fanok.audiobooks.interface_pacatge.searchable;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.pojo.SearcheblPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;

public interface SearchableModel {

    SearcheblPOJO getSearcheblPOJO(@NonNull String url) throws IOException;

    Observable<SearcheblPOJO> dowland(SharedPreferences preferences, ArrayList<String> urls, String query);
}
