package com.fanok.audiobooks.activity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.AppThemes;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.databinding.ActivityCookiesBinding;
import com.fanok.audiobooks.net.SiteHttp;
import com.r0adkll.slidr.Slidr;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.jsoup.Connection;


/**
 * Lets the user pass the site's browser check; the session cookie is kept only once a real request with it
 * returns a normal page (UI-17). Leaving early never touches the stored session.
 */
public class ActivityCookies extends AppCompatActivity {

    private static final String TAG = "ActivityCookies";
    private static final String SESSION_COOKIE = "PHPSESSID";

    private ActivityCookiesBinding binding;
    private Disposable verification;
    private String lastTried = "";
    private boolean verified;


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppThemes.apply(this, R.style.AppTheme_NoAnimTheme, R.style.LightAppTheme_NoAnimTheme,
                R.style.AppThemeBlack_NoAnimTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityCookiesBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        Slidr.attach(this);

        setSupportActionBar(binding.toolbar);
        setTitle(Url.hostOf(Url.SERVER_BAZA_KNIG));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Toast.makeText(this, R.string.wait_loading, Toast.LENGTH_LONG).show();
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                verifyCurrentSession();
            }
        });
        binding.webView.getSettings().setLoadsImagesAutomatically(true);
        binding.webView.getSettings().setJavaScriptEnabled(true);
        binding.webView.getSettings().setUserAgentString(Consts.USER_AGENT);
        binding.webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        binding.webView.loadUrl(Url.SERVER_BAZA_KNIG);
    }

    /** Checks the WebView's current session once per distinct value: one GET of the home page through SiteHttp. */
    private void verifyCurrentSession() {
        final String session = sessionCookie(CookieManager.getInstance().getCookie(Url.SERVER_BAZA_KNIG));
        if (session.isEmpty() || session.equals(lastTried) || session.equals(Consts.getBazaKnigCookies())) {
            return;
        }
        lastTried = session;
        if (verification != null) {
            verification.dispose();
        }
        verification = Single.fromCallable(() -> {
                    Connection connection = SiteHttp.connect(Url.SERVER_BAZA_KNIG).cookie(SESSION_COOKIE, session);
                    SiteHttp.parse(SiteHttp.execute(connection)); // a challenge page throws
                    return session;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
                    pref.edit().putString("cookes_baza_knig", ok).apply();
                    Consts.setBazaKnigCookies(ok);
                    verified = true;
                    Toast.makeText(this, R.string.cookies_loaded, Toast.LENGTH_SHORT).show();
                }, e -> {
                    Log.i(TAG, "Session not accepted yet: " + e);
                    lastTried = ""; // the next page load may try again
                });
    }

    /** The value of {@code PHPSESSID} in a {@code Cookie} header string, or "" when absent or malformed. */
    @NonNull
    static String sessionCookie(@Nullable String cookieHeader) {
        if (cookieHeader == null) {
            return "";
        }
        for (String pair : cookieHeader.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (SESSION_COOKIE.equals(pair.substring(0, eq).trim())) {
                return pair.substring(eq + 1).trim();
            }
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        if (verification != null) {
            verification.dispose();
        }
        if (isFinishing() && !verified) {
            Toast.makeText(this, R.string.cookies_not_verified, Toast.LENGTH_LONG).show();
        }
        if (binding != null) {
            binding.webView.stopLoading();
            binding.webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
