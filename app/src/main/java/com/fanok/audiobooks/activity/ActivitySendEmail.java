package com.fanok.audiobooks.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
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
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActivitySendEmail extends MvpAppCompatActivity {


    private ActivitySendEmailBinding binding;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();

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

            binding.progressBar.setVisibility(View.VISIBLE);

            // Получаем данные из полей ввода
            String email = binding.emailInput.getText().toString();
            String subject = binding.spinner.getSelectedItem().toString();
            String userMessage = binding.messageInput.getText().toString();

            // Запускаем фоновую задачу
            executeInBackground(() -> {
                // --- Эта часть кода выполняется в фоновом потоке ---

                // Формируем текст сообщения
                StringBuilder systemInfo = new StringBuilder();
                systemInfo.append("<b>Модель телефона:</b> ").append(Build.MODEL).append("\n");
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    systemInfo.append("<b>Игнор. оптимизацию батареи:</b> ").append(
                            pm.isIgnoringBatteryOptimizations("com.fanok.audiobooks"));
                }

                String messageText = "<b>Новое сообщение из Audiobooks!</b>\n\n" +
                        "<b>Тема:</b> " + subject + "\n" +
                        "<b>Email для ответа:</b> " + email + "\n\n" +
                        "<b>--- Сообщение пользователя ---</b>\n" + userMessage + "\n\n" +
                        "<b>--- Системная информация ---</b>\n" + systemInfo;

                String botToken = "8215660724:AAHFRPOblghARinIzWlqMnfroK0LJMF5efo";
                String chatId = "234305064";
                String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";

                try {
                    JSONObject json = new JSONObject();
                    json.put("chat_id", chatId);
                    json.put("text", messageText);
                    json.put("parse_mode", "HTML");

                    RequestBody body = RequestBody.create(json.toString(),
                            MediaType.get("application/json; charset=utf-8"));

                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url(urlString)
                            .post(body)
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        Log.d("TelegramResponse", "Response: " + response.body().string());
                        return response.isSuccessful(); // Возвращаем результат
                    }
                } catch (IOException | JSONException e) {
                    Log.e("TelegramError", "Error sending message", e);
                    return false; // Возвращаем результат
                }
            }, (Boolean success) -> {

                binding.progressBar.setVisibility(View.GONE);
                if (success) {
                    Toast.makeText(ActivitySendEmail.this, getString(R.string.email_send),
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(ActivitySendEmail.this, getString(R.string.error_send_mail),
                            Toast.LENGTH_SHORT).show();
                }
            });

        }
    }

    private <T> void executeInBackground(java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> callback) {

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                final T result = task.call();
                handler.post(() -> callback.accept(result));
            } catch (Exception e) {
                Log.e("BackgroundTask", "Error executing background task", e);
            }
        });
    }


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
