package com.fanok.audiobooks.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivitySendEmailBinding;
import com.r0adkll.slidr.Slidr;
import java.util.Objects;
import java.util.regex.Matcher;

public class ActivitySendEmail extends MvpAppCompatActivity {

    private ActivitySendEmailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySendEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Slidr.attach(this);
        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // the address the message goes to: the fork's author, filled in and still editable
        binding.emailInput.setText(R.string.feedback_email);

        Intent intent = getIntent();
        String message = intent.getStringExtra("message");
        if (message != null) {
            binding.messageInput.setText(message);
        }
        boolean enabled = intent.getBooleanExtra("enebled", true);
        binding.messageInput.setEnabled(enabled);
        binding.spinner.setEnabled(enabled);

        int subject = intent.getIntExtra("subject", 0);
        binding.spinner.setSelection(subject);

        binding.emailInput.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                if (!validateEmail(Objects.requireNonNull(binding.emailInput.getText()).toString())) {
                    binding.emailLayout.setError(getString(R.string.incorect_email));
                    binding.emailLayout.setErrorEnabled(true);
                }
            } else {
                binding.emailLayout.setErrorEnabled(false);
            }
        });

        binding.messageInput.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                if (!validateEmpty(Objects.requireNonNull(binding.messageInput.getText()).toString())) {
                    binding.messageLayout.setError(getString(R.string.empty_text));
                    binding.messageLayout.setErrorEnabled(true);
                }
            } else {
                binding.messageLayout.setErrorEnabled(false);
            }
        });

        binding.send.setOnClickListener(view -> click());

    }

    private boolean validateEmpty(@NonNull String text) {
        return !text.isEmpty();
    }

    private boolean validateEmail(@NonNull String email) {
        Matcher matcher = Consts.REGEXP_EMAIL.matcher(email);
        return matcher.find();
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            theme.applyStyle(R.style.AppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_SwipeOnClose, true);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_SwipeOnClose, true);
        }

        return theme;
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void click() {
        if (!validateEmail(Objects.requireNonNull(binding.emailInput.getText()).toString())) {
            binding.emailLayout.setError(getString(R.string.incorect_email));
            binding.emailLayout.setErrorEnabled(true);
        }

        if (!validateEmpty(Objects.requireNonNull(binding.messageInput.getText()).toString())) {
            binding.messageLayout.setError(getString(R.string.empty_text));
            binding.messageLayout.setErrorEnabled(true);
        }

        if (!binding.messageLayout.isErrorEnabled() && !binding.emailLayout.isErrorEnabled()) {
            String email = binding.emailInput.getText().toString().trim();
            String subject = binding.spinner.getSelectedItem().toString();
            String body = binding.messageInput.getText().toString() + "\n\n"
                    + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE;

            // The user's own mail app sends the message, so they see the recipient and the text.
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email
                    + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)));
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, body);
            try {
                startActivity(intent);
                finish();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, getString(R.string.error_send_mail), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
