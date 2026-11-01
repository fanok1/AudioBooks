package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class LoadBook extends AppCompatActivity {

    private BookPOJO mBookPOJO;
    private String mUrl;
    private Context mContext;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.load_book_activity);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoAnimTheme);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoAnimTheme);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_NoAnimTheme);
        }

        mContext = this;
        Intent intent = getIntent();
        mUrl = bookUrl(intent.getStringExtra("url"), intent.getData() == null ? null : intent.getData().toString());
        if (mUrl == null) {
            // a deep link to a host the app does not scrape, or no URL at all
            Toast.makeText(this, R.string.unknown_source, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // never trusted from an intent: this activity is exported, so the PIN check always runs (SEC-05)
        if (mUrl.equals(BookActivity.getShowingView())) finish();
    }

    /** The provider book URL from the explicit extra or the VIEW data, on its current host; null when unknown. */
    static String bookUrl(String extra, String data) {
        String url = extra != null ? extra : data;
        if (url == null || Provider.ofUrl(url) == null) {
            return null;
        }
        return Url.canonical(url);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mUrl == null) {
            return;
        }
        BookPOJO.getDescription(mUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BookPOJO>() {
                    @Override
                    public void onError(@NotNull Throwable e) {
                        int message;
                        if (e instanceof CookesExeption) {
                            message = CookesExeption.messageFor(e.getMessage());
                        } else if (e instanceof IllegalArgumentException
                                && String.valueOf(e.getMessage()).startsWith("unsupported host")) {
                            message = R.string.worong_data_sorce;
                        } else {
                            message = R.string.error_load_data;
                        }
                        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                        finish();
                    }

                    @Override
                    public void onNext(@NotNull BookPOJO bookPOJO) {
                        mBookPOJO = bookPOJO;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }

                    @Override
                    public void onComplete() {
                        BookActivity.startNewActivity(mContext, mBookPOJO);
                    }
                });
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_NoAnimTheme, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_NoAnimTheme, true);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_NoAnimTheme, true);
        }


        return theme;
    }

}
