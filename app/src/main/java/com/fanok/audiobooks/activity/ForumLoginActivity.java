package com.fanok.audiobooks.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.fanok.audiobooks.AppThemes;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivityCookiesBinding;
import com.fanok.audiobooks.update.Updater;
import com.r0adkll.slidr.Slidr;

/**
 * The 4pda post with the update's file, in a WebView: the user logs in to the forum (and passes its browser check).
 * The WebView keeps both in its cookies; when the user leaves, or taps the file, the {@link Updater} fetches the file
 * itself with them.
 */
public class ForumLoginActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "url";

    private ActivityCookiesBinding binding;

    public static void start(@NonNull Context context, @NonNull String url) {
        context.startActivity(new Intent(context, ForumLoginActivity.class).putExtra(EXTRA_URL, url));
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppThemes.apply(this, R.style.AppTheme_NoAnimTheme, R.style.LightAppTheme_NoAnimTheme,
                R.style.AppThemeBlack_NoAnimTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityCookiesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Slidr.attach(this);

        setSupportActionBar(binding.toolbar);
        setTitle("4pda");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // the WebView's own agent: the site's browser check is bound to it, and the Updater sends the same one
        Updater.rememberForumAgent(this, settings.getUserAgentString());
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true);
        binding.webView.setWebViewClient(new WebViewClient());
        // the file itself: the Updater downloads it with a fresh link of its own
        binding.webView.setDownloadListener((url, userAgent, disposition, mimeType, length) -> finish());
        binding.webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        if (savedInstanceState == null) {
            Toast.makeText(this, R.string.update_forum_hint, Toast.LENGTH_LONG).show();
        }
        binding.webView.loadUrl(getIntent().getStringExtra(EXTRA_URL));
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            binding.webView.stopLoading();
            binding.webView.destroy();
        }
        if (isFinishing()) {
            Updater.forumLeft(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
