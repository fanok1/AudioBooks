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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.r0adkll.slidr.Slidr;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActivitySendEmail extends MvpAppCompatActivity {


    @BindView(R.id.emailInput)
    TextInputEditText mEmailInput;
    @BindView(R.id.emailLayout)
    TextInputLayout mEmailLayout;
    @BindView(R.id.spinner)
    Spinner mSpinner;
    @BindView(R.id.send)
    Button mSend;
    @BindView(R.id.messageInput)
    TextInputEditText mMessageInput;
    @BindView(R.id.messageLayout)
    TextInputLayout mMessageLayout;
    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_email);
        ButterKnife.bind(this);
        Slidr.attach(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        String message = intent.getStringExtra("message");
        if (message != null) {
            mMessageInput.setText(message);
        }
        boolean enabled = intent.getBooleanExtra("enebled", true);
        mMessageInput.setEnabled(enabled);
        mSpinner.setEnabled(enabled);

        int subject = intent.getIntExtra("subject", 0);
        mSpinner.setSelection(subject);

        mEmailInput.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                if (!validateEmail(Objects.requireNonNull(mEmailInput.getText()).toString())) {
                    mEmailLayout.setError(getString(R.string.incorect_email));
                    mEmailLayout.setErrorEnabled(true);
                }
            } else {
                mEmailLayout.setErrorEnabled(false);
            }
        });

        mMessageInput.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                if (!validateEmpty(Objects.requireNonNull(mMessageInput.getText()).toString())) {
                    mMessageLayout.setError(getString(R.string.empty_text));
                    mMessageLayout.setErrorEnabled(true);
                }
            } else {
                mMessageLayout.setErrorEnabled(false);
            }
        });

        mSend.setOnClickListener(view -> click());

    }

    private boolean validateEmpty(@NonNull String text) {
        return !text.isEmpty();
    }

    private boolean validateEmail(@NonNull String email) {
        Matcher matcher = Consts.REGEXP_EMAIL.matcher(email);
        return matcher.find();
    }

    @SuppressLint("StaticFieldLeak")
    private void click() {
        if (!validateEmail(Objects.requireNonNull(mEmailInput.getText()).toString())) {
            mEmailLayout.setError(getString(R.string.incorect_email));
            mEmailLayout.setErrorEnabled(true);
        }

        if (!validateEmpty(Objects.requireNonNull(mMessageInput.getText()).toString())) {
            mMessageLayout.setError(getString(R.string.empty_text));
            mMessageLayout.setErrorEnabled(true);
        }

        if (!mMessageLayout.isErrorEnabled() && !mEmailLayout.isErrorEnabled()) {

            new AsyncTask<Void, Void, Boolean>() {

                String email;
                String message;
                String sunject;


                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    mProgressBar.setVisibility(View.VISIBLE);

                    StringBuilder builder = new StringBuilder();
                    builder.append("email = ").append(mEmailInput.getText().toString()).append(
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
                    builder.append(mMessageInput.getText().toString().replaceAll("\n", "<br/>"));

                    email = mEmailInput.getText().toString();
                    sunject = mSpinner.getSelectedItem().toString();
                    message = builder.toString();
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    super.onPostExecute(aBoolean);
                    mProgressBar.setVisibility(View.GONE);
                    if (aBoolean) {
                        Toast.makeText(ActivitySendEmail.this, getString(R.string.email_send),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ActivitySendEmail.this, getString(R.string.error_send_mail),
                                Toast.LENGTH_SHORT).show();
                    }
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
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
        }


        return theme;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }
}
