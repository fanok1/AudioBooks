package com.fanok.audiobooks.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.import_favorite.ActivityImportInterface;
import com.fanok.audiobooks.presenter.ImportPresenter;
import com.r0adkll.slidr.Slidr;

import org.jetbrains.annotations.NotNull;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ActivityImport extends MvpAppCompatActivity implements ActivityImportInterface {

    private static final String IMPORT = "IMPORT_SITE";
    @InjectPresenter
    ImportPresenter mPresenter;
    @BindView(R.id.login)
    Button mLogin;
    @BindView(R.id.password)
    EditText mPassword;
    @BindView(R.id.username)
    EditText mUsername;
    @BindView(R.id.loading)
    ProgressBar mLoading;

    public static void startActivity(@NotNull Activity activity, int site) {
        Intent intent = new Intent(activity, ActivityImport.class);
        intent.putExtra(IMPORT, site);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.enter, R.anim.exit);

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);
        ButterKnife.bind(this);
        Slidr.attach(this);
        Intent intent = getIntent();
        int site = intent.getIntExtra(IMPORT, -1);
        if (site == -1) throw new IllegalArgumentException();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        mUsername.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                mPresenter.validate((EditText) view);
            }
        });

        mPassword.setOnFocusChangeListener((view, b) -> {
            if (!b) {
                mPresenter.validate((EditText) view);
            }
        });

        mLogin.setOnClickListener(view -> click());


        mPassword.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                click();
                return true;
            }
            return false;
        });

        mPresenter.onCreate(this, site);

    }

    private void click() {
        mPresenter.validate(mUsername);
        mPresenter.validate(mPassword);

        if (mUsername.getError() == null && mPassword.getError() == null) {
            mPresenter.login(mUsername.getText().toString(), mPassword.getText().toString());
        }
    }

    @Override
    public void showToast(int id) {
        Toast.makeText(this, getText(id), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void close() {
        finish();
    }

    @Override
    public void showProgress(boolean b) {
        if (b) {
            mLoading.setVisibility(View.VISIBLE);
        } else {
            mLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left);
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
