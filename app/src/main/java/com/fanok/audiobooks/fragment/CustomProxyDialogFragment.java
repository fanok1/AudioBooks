package com.fanok.audiobooks.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.net.ProxyConfig;
import com.fanok.audiobooks.search.Provider;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Locale;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * "Свой прокси": the server of the built-in proxy mode — type, address, port and an optional login — with a check
 * through exactly what is typed, before anything is saved. The build's own password stays dots: the eye shows only a
 * password the user types, and a field the user leaves empty gets the build's back. "Сохранить" applies at once; the
 * check box turns the built-in mode on, or off when it was the mode.
 */
public class CustomProxyDialogFragment extends DialogFragment {

    public static final String TAG = "CustomProxyDialogFragment";
    /** Sent to the parent's fragment manager when the dialog closes. */
    public static final String RESULT_CLOSED = "custom_proxy_dialog_closed";
    private static final String STATE_OWN_PASSWORD = "own_password";
    private static final String STATE_CLEARED_OWN = "cleared_own";
    /** What the password field shows for the build's password: never the password itself (a restored eye shows it). */
    private static final String OWN_PASSWORD_DOTS = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
    private static final int OK_COLOR = 0xFF4CAF50;
    private static final int FAIL_COLOR = 0xFFE53935;

    private RadioGroup type;
    private TextInputLayout hostLayout;
    private TextInputLayout portLayout;
    private TextInputLayout userLayout;
    private TextInputLayout passwordLayout;
    private EditText host;
    private EditText port;
    private EditText user;
    private EditText password;
    private CheckBox use;
    private View dot;
    private ProgressBar progress;
    private TextView status;
    /** The password field holds the build's password: dots only, no eye. */
    private boolean ownPassword;
    /** The build's password was cleared for typing and nothing is typed yet: leaving the field brings it back. */
    private boolean clearedOwn;
    /** The code, not the user, is changing the password field. */
    private boolean filling;
    private Disposable check;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_custom_proxy, null, false);
        type = view.findViewById(R.id.customProxyType);
        hostLayout = view.findViewById(R.id.customProxyHostLayout);
        portLayout = view.findViewById(R.id.customProxyPortLayout);
        userLayout = view.findViewById(R.id.customProxyUserLayout);
        passwordLayout = view.findViewById(R.id.customProxyPasswordLayout);
        host = view.findViewById(R.id.customProxyHost);
        port = view.findViewById(R.id.customProxyPort);
        user = view.findViewById(R.id.customProxyUser);
        password = view.findViewById(R.id.customProxyPassword);
        use = view.findViewById(R.id.customProxyUse);
        dot = view.findViewById(R.id.customProxyDot);
        progress = view.findViewById(R.id.customProxyProgress);
        status = view.findViewById(R.id.customProxyStatus);

        if (savedInstanceState == null) {
            fill(AppProxy.custom());
            use.setChecked(AppProxy.MODE_BUILTIN.equals(AppProxy.mode()));
        } else { // the fields come back with the dialog's own state
            ownPassword = savedInstanceState.getBoolean(STATE_OWN_PASSWORD);
            clearedOwn = savedInstanceState.getBoolean(STATE_CLEARED_OWN);
            passwordLayout.setEndIconVisible(!ownPassword);
        }
        password.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && ownPassword) {
                setPassword("", false); // typing replaces the build's password, never edits it
                clearedOwn = true;
            } else if (!hasFocus && clearedOwn && password.length() == 0) {
                setPassword("", true);
            }
        });
        TextWatcher edited = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!filling && s == password.getText() && s.length() > 0) {
                    clearedOwn = false;
                } else if (!filling && s == user.getText() && s.length() == 0 && ownPassword) {
                    setPassword("", false); // no login, no password: the build's goes with the build's login
                } else if (!filling && (s == host.getText() || s == port.getText()) && ownPassword) {
                    forgetOwnLoginElsewhere();
                }
                showResult(null, 0); // a result belongs to the values it checked
            }
        };
        for (EditText field : new EditText[]{host, port, user, password}) {
            field.addTextChangedListener(edited);
        }
        type.setOnCheckedChangeListener((group, checkedId) -> showResult(null, 0));
        view.findViewById(R.id.customProxyCheck).setOnClickListener(v -> runCheck());
        view.findViewById(R.id.customProxyReset).setOnClickListener(v -> {
            fill(ProxyConfig.builtIn());
            showResult(null, 0);
        });

        return builder.setTitle(R.string.proxy_custom_title)
                .setView(view)
                .setPositiveButton(R.string.proxy_custom_save, null)
                .setNegativeButton(R.string.proxy_custom_cancel, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        if (dialog.getWindow() != null) {
            // the keyboard pushes the fields up instead of covering the lower ones
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // the dialog stays open until the values are valid
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> save());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_OWN_PASSWORD, ownPassword);
        outState.putBoolean(STATE_CLEARED_OWN, clearedOwn);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (check != null) {
            check.dispose();
        }
        super.onDismiss(dialog);
        if (isAdded()) {
            getParentFragmentManager().setFragmentResult(RESULT_CLOSED, new Bundle());
        }
    }

    private void fill(@NonNull ProxyConfig config) {
        type.check(ProxyConfig.TYPE_HTTP.equals(config.type) ? R.id.customProxyHttp : R.id.customProxySocks);
        host.setText(config.host);
        port.setText(config.port > 0 ? String.valueOf(config.port) : "");
        user.setText(config.user);
        setPassword(config.ownPassword ? "" : config.password, config.ownPassword);
        clearedOwn = false;
        hostLayout.setError(null);
        portLayout.setError(null);
        userLayout.setError(null);
    }

    /** Another server than the build's gets no login of the build's: the fields are cleared for the user's own. */
    private void forgetOwnLoginElsewhere() {
        ProxyConfig own = ProxyConfig.builtIn();
        if (own.isAt(host.getText().toString().trim(), ProxyConfig.port(port.getText().toString()))) {
            return;
        }
        setPassword("", false);
        if (user.getText().toString().trim().equals(own.user)) {
            filling = true;
            user.setText("");
            filling = false;
        }
    }

    /** The password field's text; the build's password is only a row of dots there, with no eye. */
    private void setPassword(@NonNull String text, boolean own) {
        filling = true;
        password.setText(own ? OWN_PASSWORD_DOTS : text);
        filling = false;
        ownPassword = own;
        if (own) {
            clearedOwn = false;
            password.setTransformationMethod(PasswordTransformationMethod.getInstance()); // the eye may have shown text
        }
        passwordLayout.setEndIconVisible(!own);
    }

    /** The typed values, or null after marking the field to fix. */
    @Nullable
    private ProxyConfig typed() {
        String typeValue = type.getCheckedRadioButtonId() == R.id.customProxyHttp
                ? ProxyConfig.TYPE_HTTP : ProxyConfig.TYPE_SOCKS;
        // cleared for typing and left empty (Save still in the field): the build's password, not none
        boolean own = ownPassword || clearedOwn && password.length() == 0;
        ProxyConfig config = new ProxyConfig(typeValue, host.getText().toString(),
                ProxyConfig.port(port.getText().toString()), user.getText().toString().trim(),
                own ? "" : password.getText().toString(), own);
        if (own && !config.isBuiltInServer()) { // never the build's login to another server
            config = new ProxyConfig(typeValue, config.host, config.port, config.user, "", false);
        }
        ProxyConfig.Problem problem = ProxyConfig.check(config.host, config.port, config.user, config.password);
        hostLayout.setError(problem == ProxyConfig.Problem.HOST ? getString(R.string.proxy_custom_error_host) : null);
        portLayout.setError(problem == ProxyConfig.Problem.PORT ? getString(R.string.proxy_custom_error_port) : null);
        userLayout.setError(problem == ProxyConfig.Problem.USER ? getString(R.string.proxy_custom_error_user) : null);
        switch (problem) {
            case HOST:
                host.requestFocus();
                return null;
            case PORT:
                port.requestFocus();
                return null;
            case USER:
                user.requestFocus();
                return null;
            default:
                return config;
        }
    }

    /** The typed proxy, checked against the selected source's site: the site, not just the proxy, has to answer. */
    private void runCheck() {
        ProxyConfig config = typed();
        if (config == null) {
            return;
        }
        if (check != null) {
            check.dispose();
        }
        Provider selected = Provider.current();
        String server = (selected != null ? selected : Provider.AKNIGA).server;
        String site = Url.hostOf(server);
        dot.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.proxy_custom_checking);
        check = AppProxy.check(config, server + "/")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> showResult(text(result, site), worked(result) ? OK_COLOR : FAIL_COLOR),
                        e -> showResult(getString(R.string.proxy_custom_failed, e.toString()), FAIL_COLOR));
    }

    /** The check's line with its dot; null clears both. */
    private void showResult(@Nullable CharSequence text, int color) {
        if (status == null) {
            return;
        }
        if (text == null && check != null) {
            check.dispose(); // its values changed: its answer would be about other ones
        }
        progress.setVisibility(View.GONE);
        dot.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) {
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(color));
        }
        status.setText(text);
    }

    /** Any answer of the site counts: a 403 came through the proxy as well. Only the proxy's own 407 does not. */
    static boolean worked(@NonNull AppProxy.Check result) {
        return result.failure == null && result.code != 407;
    }

    @NonNull
    private String text(@NonNull AppProxy.Check result, @NonNull String site) {
        if (worked(result)) {
            float seconds = result.millis / 1000f;
            return result.code >= 200 && result.code < 400
                    ? getString(R.string.proxy_custom_ok, site, seconds)
                    : getString(R.string.proxy_custom_ok_code, site, seconds, result.code);
        }
        IOException e = result.failure;
        if (result.code == 407 || loginRefused(e)) {
            return getString(R.string.proxy_custom_login_refused);
        }
        String message = messages(e);
        if (caused(e, UnknownHostException.class)) {
            return getString(R.string.proxy_custom_unknown_host);
        }
        // a SOCKS connection that timed out comes wrapped, its timeout only in the text
        if (message.contains("failed to connect") && (message.contains("after") || message.contains("timed out"))) {
            return getString(R.string.proxy_custom_silent);
        }
        if (caused(e, ConnectException.class) || caused(e, NoRouteToHostException.class)
                || message.contains("refused")) {
            return getString(R.string.proxy_custom_refused);
        }
        if (caused(e, InterruptedIOException.class) || message.contains("timeout") || message.contains("timed out")) {
            return getString(R.string.proxy_custom_no_answer); // a timeout of the whole call: proxy or site
        }
        String reason = e == null ? "" : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return getString(R.string.proxy_custom_failed, reason);
    }

    /** A SOCKS login refused ("SOCKS : authentication failed") or an HTTP proxy's ("Failed to authenticate…"). */
    static boolean loginRefused(@Nullable IOException e) {
        return messages(e).contains("authenticat");
    }

    /** {@code e} or one of its causes is a {@code type}. */
    static boolean caused(@Nullable Throwable e, @NonNull Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /** The messages of {@code e} and its causes, lower case. */
    @NonNull
    static String messages(@Nullable Throwable e) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null) {
                all.append(t.getMessage().toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        return all.toString();
    }

    private void save() {
        ProxyConfig config = typed();
        Context context = getContext();
        if (config == null || context == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // the build's values themselves are not stored: they follow the build
        AppProxy.setCustom(prefs, config.ownPassword && config.equals(ProxyConfig.builtIn()) ? null : config);
        if (use.isChecked()) {
            if (!AppProxy.MODE_BUILTIN.equals(AppProxy.mode())) {
                AppProxy.setMode(context, AppProxy.MODE_BUILTIN);
            }
        } else if (AppProxy.MODE_BUILTIN.equals(AppProxy.mode())) {
            AppProxy.setMode(context, AppProxy.MODE_OFF);
        }
        dismiss();
    }
}
