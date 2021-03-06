package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class LoadBook extends AppCompatActivity {

    private BookPOJO mBookPOJO;
    private String mUrl;
    private boolean mNotificationClick;
    private Context mContext;

    @Override
    public void onBackPressed() {

    }

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
        mUrl = intent.getStringExtra("url");
        if (mUrl == null && intent.getData() != null) {
            mUrl = intent.getData().toString();
            if (mUrl.contains("knigavuhe.org")) {
                if (!mUrl.contains("/book/")) {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                } else {
                    mUrl = mUrl.replace("https://m.", "https://");
                }
            } else if (mUrl.contains("izib.uk")) {
                if (!mUrl.contains("/book")) {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                } else {
                    mUrl = mUrl.replace("https://pda.", "https://");
                }

            } else if (mUrl.contains("audiobook-mp3.com")) {
                if (!mUrl.contains("/audio")) {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                }
            } else {
                finish();
            }
        }
        mNotificationClick = intent.getBooleanExtra("notificationClick", false);
        String text = BookActivity.getShowingView();
        if (mUrl == null || mUrl.equals(text)) finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BookPOJO.getDescription(mUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BookPOJO>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(BookPOJO bookPOJO) {
                        mBookPOJO = bookPOJO;
                    }

                    @Override
                    public void onError(Throwable e) {
                        finish();
                    }

                    @Override
                    public void onComplete() {
                        BookActivity.startNewActivity(mContext, mBookPOJO, mNotificationClick);
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

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }
}
