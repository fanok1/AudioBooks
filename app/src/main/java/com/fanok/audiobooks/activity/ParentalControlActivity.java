package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.ParentalControlAddapter;
import com.fanok.audiobooks.databinding.ActivityParentalControlBinding;
import com.fanok.audiobooks.presenter.ParentalControlPresenter;
import com.r0adkll.slidr.Slidr;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;


public class ParentalControlActivity extends MvpAppCompatActivity implements
        com.fanok.audiobooks.interface_pacatge.parental_control.View {

    public static final String PARENTAL_CONTROL_PREFERENCES = "Parental Control";

    public static final String PARENTAL_CONTROL_ENABLED = "Parental Control Enabled";

    public static final String PARENTAL_PASSWORD = "Parental Password";

    private static boolean isShowDialog;

    @InjectPresenter
    ParentalControlPresenter mPresenter;

    private SharedPreferences mSettings;

    private Switch parentalControl;

    private AlertDialog mAlertDialog;

    private ParentalControlAddapter mAddapter;

    private ActivityParentalControlBinding binding;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityParentalControlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            isShowDialog = false;
        }

        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        Slidr.attach(this);
        mSettings = getSharedPreferences(PARENTAL_CONTROL_PREFERENCES, MODE_PRIVATE);

        XmlPullParser parser = getResources().getXml(R.xml.pin_entry);
        try {
            parser.next();
            parser.nextTag();
        } catch (Exception e) {
            e.printStackTrace();
        }

        AttributeSet attr = Xml.asAttributeSet(parser);

        PinEntryEditText editText = new PinEntryEditText(this, attr);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enter_new_password)
                .setCancelable(true)
                .setView(editText)
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null);

        mAlertDialog = builder.create();
        mAlertDialog.setOnShowListener(dialogInterface -> {
            Objects.requireNonNull(editText.getText()).clear();
            editText.requestFocus();
            Button b = mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setEnabled(false);
            b.setOnClickListener(
                    view -> {
                        SharedPreferences.Editor editor = mSettings.edit();
                        if (parentalControl != null) {
                            editor.putInt(PARENTAL_PASSWORD,
                                    Integer.parseInt(editText.getText().toString()));
                        }
                        editor.apply();
                        parentalControl.setChecked(true);
                        mAlertDialog.cancel();
                    });
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                int size = Objects.requireNonNull(editText.getText()).toString().length();
                mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(size == 4);

            }
        });

        editText.setOnPinEnteredListener(str -> {
            SharedPreferences.Editor editor = mSettings.edit();
            if (parentalControl != null) {
                editor.putInt(PARENTAL_PASSWORD,
                        Integer.parseInt(Objects.requireNonNull(editText.getText()).toString()));
            }
            editor.apply();
            parentalControl.setChecked(true);
            mAlertDialog.cancel();
        });

        if (mSettings.getBoolean(PARENTAL_CONTROL_ENABLED, false) && !isShowDialog) {
            showDialog(attr);
        }

        binding.parentalControlParent.setLayoutManager(new LinearLayoutManager(this));
        mAddapter = new ParentalControlAddapter(this);
        binding.parentalControlParent.setAdapter(mAddapter);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.parental_control_menu, menu);
        MenuItem switshMenuItem = menu.findItem(R.id.app_bar_switch);
        parentalControl = switshMenuItem.getActionView().findViewById(R.id.switchView);
        parentalControl.setOnCheckedChangeListener(
                (compoundButton, b) -> {
                    mAddapter.setEnabled(b);
                    SharedPreferences.Editor editor = mSettings.edit();
                    editor.putBoolean(PARENTAL_CONTROL_ENABLED, b);
                    editor.apply();
                });
        parentalControl.setOnClickListener(view -> {
            if (parentalControl.isChecked()) {
                parentalControl.setChecked(false);
                mAlertDialog.show();
            }
        });
        parentalControl.setChecked(mSettings.getBoolean(PARENTAL_CONTROL_ENABLED, false));
        if (getIntent().getBooleanExtra("enabled", false) && !parentalControl.isChecked()) {
            mAlertDialog.show();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    public void showProgress(boolean b) {
        if (b) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    void showDialog(AttributeSet attr) {

        PinEntryEditText editText = new PinEntryEditText(this, attr);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enter_password)
                .setCancelable(false)
                .setView(editText)
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Objects.requireNonNull(editText.getText()).clear();
            editText.requestFocus();
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setEnabled(false);
            b.setOnClickListener(view -> {
                if (mSettings.getInt(PARENTAL_PASSWORD, 0) == Integer.parseInt(
                        editText.getText().toString())) {
                    isShowDialog = true;
                    dialog.cancel();
                } else {
                    Toast.makeText(ParentalControlActivity.this,
                            getString(R.string.incorect_password),
                            Toast.LENGTH_SHORT).show();
                    editText.getText().clear();
                }
            });

            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            negativeButton.setOnClickListener(view -> finish());
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                int size = Objects.requireNonNull(editText.getText()).toString().length();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(size == 4);
            }
        });

        editText.setOnPinEnteredListener(str -> {
            if (mSettings.getInt(PARENTAL_PASSWORD, 0) == Integer.parseInt(
                    Objects.requireNonNull(editText.getText()).toString())) {
                isShowDialog = true;
                dialog.cancel();
            } else {
                Toast.makeText(ParentalControlActivity.this,
                        getString(R.string.incorect_password),
                        Toast.LENGTH_SHORT).show();
                editText.getText().clear();
            }
        });

        dialog.show();
    }

    @Override
    public void showData(@NotNull ArrayList<String> arrayList) {
        mAddapter.setItem(arrayList);
    }

    @Override
    public void showToast(int id) {
        Toast.makeText(this, getString(id), Toast.LENGTH_SHORT).show();
    }
}
