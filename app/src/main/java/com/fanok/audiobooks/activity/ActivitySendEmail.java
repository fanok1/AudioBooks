package com.fanok.audiobooks.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivitySendEmailBinding;
import com.r0adkll.slidr.Slidr;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

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

    @SuppressLint("StaticFieldLeak")
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

            new AsyncTask<Void, Void, Boolean>() {

                String email;

                String message;

                String sunject;


                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    super.onPostExecute(aBoolean);
                    binding.progressBar.setVisibility(View.GONE);
                    if (aBoolean) {
                        Toast.makeText(ActivitySendEmail.this, getString(R.string.email_send),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ActivitySendEmail.this, getString(R.string.error_send_mail),
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    binding.progressBar.setVisibility(View.VISIBLE);

                    StringBuilder builder = new StringBuilder();
                    builder.append("email = ").append(binding.emailInput.getText().toString()).append(
                            "<br/>");
                    builder.append("phone = ").append(Build.MODEL).append("<br/>");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        if (pm != null) {
                            builder.append("isIgnoringBatteryOptimizations = ").append(
                                    pm.isIgnoringBatteryOptimizations(
                                            "com.fanok.audiobooks")).append("<br/>");
                        }
                    }

                    builder.append("<br/>").append("<br/>");
                    builder.append(binding.messageInput.getText().toString().replaceAll("\n", "<br/>"));

                    email = binding.emailInput.getText().toString();
                    sunject = binding.spinner.getSelectedItem().toString();
                    message = builder.toString();
                }

                @Override
                protected Boolean doInBackground(Void... voids) {
                    JSONObject json = new JSONObject();
                    try {
                        json.put("email", email);
                        json.put("subject", sunject);
                        json.put("message", message);


                        RequestBody body = RequestBody.create(
                                MediaType.get("application/json; charset=utf-8"), json.toString());

                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder()
                                .url("http://mdpu.mcdir.ru/audiobooks_send_mail.php")
                                .post(body)
                                .build();
                        try (Response response = client.newCall(request).execute()) {
                            String result = Objects.requireNonNull(response.body()).string();
                            return result.contains("1");
                        } catch (IOException e) {
                            return false;
                        }

                    } catch (JSONException e) {
                        return false;
                    }
                }
            }.execute();

        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }
}
