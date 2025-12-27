package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivityLoginBinding;
import com.fanok.audiobooks.interface_pacatge.login.LoginView;
import com.fanok.audiobooks.presenter.LoginPresenter;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.r0adkll.slidr.Slidr;
import com.yandex.authsdk.YandexAuthLoginOptions;
import com.yandex.authsdk.YandexAuthOptions;
import com.yandex.authsdk.YandexAuthResult;
import com.yandex.authsdk.YandexAuthSdkContract;

public class LoginActivity extends MvpAppCompatActivity implements LoginView {

    @InjectPresenter
    LoginPresenter mPresenter;

    private ActivityLoginBinding binding;
    private CredentialManager credentialManager;

    private final ActivityResultLauncher<YandexAuthLoginOptions> yandexLauncher = registerForActivityResult(
            new ActivityResultContract<YandexAuthLoginOptions, YandexAuthResult>() {
                @NonNull
                @Override
                public Intent createIntent(@NonNull Context context, YandexAuthLoginOptions input) {
                    return new YandexAuthSdkContract(new YandexAuthOptions(context))
                            .createIntent(context, input);
                }

                @Override
                public YandexAuthResult parseResult(int resultCode, @Nullable Intent intent) {
                    return new YandexAuthSdkContract(new YandexAuthOptions(LoginActivity.this)) // Тут контекст уже тоже безопасен (это рантайм)
                            .parseResult(resultCode, intent);
                }
            },
            result -> mPresenter.onYandexAuthResult(result)
    );

    @ProvidePresenter
    LoginPresenter provideLoginPresenter() {
        return new LoginPresenter(getApplicationContext());
    }

    public static void start(Context context) {
        Intent starter = new Intent(context, LoginActivity.class);
        starter.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(starter);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        mPresenter.login(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter.login(getIntent());

        credentialManager = CredentialManager.create(this);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Slidr.attach(this);
        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        binding.buttonLogin.setOnClickListener(v -> {
            String email = binding.emailEditText.getText() != null ? binding.emailEditText.getText().toString() : "";
            String password = binding.passwordEditText.getText() != null ? binding.passwordEditText.getText().toString() : "";
            mPresenter.onLoginClicked(email, password);
        });

        binding.buttonRegister.setOnClickListener(v -> {
            String email = binding.emailEditText.getText() != null ? binding.emailEditText.getText().toString() : "";
            String password = binding.passwordEditText.getText() != null ? binding.passwordEditText.getText().toString() : "";
            mPresenter.onRegisterClicked(email, password);
        });

        binding.buttonForgotPassword.setOnClickListener(v -> mPresenter.onForgotPasswordClicked());

        binding.buttonGoogle.setOnClickListener(v -> mPresenter.onGoogleClicked());
        binding.buttonYandex.setOnClickListener(v -> mPresenter.onYandexClicked());
        binding.buttonTelegram.setOnClickListener(v -> mPresenter.onTelegramClicked());

    }


    @Override
    public void showToast(int messageId) {
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    public void launchYandexAuth() {
        yandexLauncher.launch(new YandexAuthLoginOptions());
    }

    @Override
    public void launchGoogleAuth() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e("LoginActivity", "getCredentialAsync failed", e);
                        mPresenter.onGoogleAuthFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
        );
    }

    @Override
    public void showForgotPassword() {
        String email = binding.emailEditText.getText() != null ? binding.emailEditText.getText().toString() : "";
        if (email.isEmpty()) {
            Toast.makeText(this, "Введите email", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Письмо для сброса пароля отправлено", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "Ошибка отправки письма", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleSignIn(GetCredentialResponse result) {
        Credential credential = result.getCredential();
        if (credential instanceof CustomCredential) {
            CustomCredential customCredential = (CustomCredential) credential;
            if (customCredential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                try {
                    GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
                    mPresenter.onGoogleAuthSuccess(googleIdTokenCredential.getIdToken());
                } catch (Exception e) {
                    Log.e("LoginActivity", "GoogleIdTokenParsingException", e);
                    mPresenter.onGoogleAuthFailed(e.getMessage());
                }
            } else {
                Log.e("LoginActivity", "Unexpected credential type: " + customCredential.getType());
                mPresenter.onGoogleAuthFailed("Unexpected credential type");
            }
        } else {
            Log.e("LoginActivity", "Unexpected credential type");
            mPresenter.onGoogleAuthFailed("Unexpected credential type");
        }
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

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

}
