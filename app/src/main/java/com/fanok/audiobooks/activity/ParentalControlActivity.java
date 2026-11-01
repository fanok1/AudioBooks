package com.fanok.audiobooks.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.alimuzaffar.lib.pin.PinEntryEditText;
import com.fanok.audiobooks.AppThemes;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.ParentalControlAddapter;
import com.fanok.audiobooks.databinding.ActivityParentalControlBinding;
import com.fanok.audiobooks.policy.ParentalControl;
import com.r0adkll.slidr.Slidr;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParser;


public class ParentalControlActivity extends AppCompatActivity {

    public static final String PARENTAL_CONTROL_PREFERENCES = ParentalControl.PREFERENCES;

    public static final String PARENTAL_CONTROL_ENABLED = ParentalControl.ENABLED;

    /**
     * @deprecated the plain-int PIN; kept only for {@code BookActivity}'s old prompt until it calls
     * {@link ParentalControl#verifyPin(android.content.Context, String)}. Once migrated, this key is gone.
     */
    @Deprecated
    public static final String PARENTAL_PASSWORD = "Parental Password";

    private static boolean isShowDialog;

    private ParentalControl.Store mStore;

    private androidx.appcompat.widget.SwitchCompat parentalControl;

    private AlertDialog mAlertDialog;

    private ParentalControlAddapter mAddapter;

    private ActivityParentalControlBinding binding;

    /** Receives the four digits the user confirmed, with the field to clear on a wrong entry. */
    private interface OnPin {
        void onPin(@NonNull String pin, @NonNull PinEntryEditText field);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppThemes.apply(this, R.style.AppTheme_SwipeOnClose, R.style.LightAppTheme_SwipeOnClose,
                R.style.AppThemeBlack_SwipeOnClose);
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
        mStore = ParentalControl.store(this);

        mAlertDialog = pinDialog(R.string.enter_new_password, true, (pin, field) -> {
            ParentalControl.setPin(mStore, pin);
            if (parentalControl != null) {
                parentalControl.setChecked(true);
            }
            mAlertDialog.cancel();
        });

        if (ParentalControl.isEnabled(mStore) && !isShowDialog) {
            showDialog();
        }

        binding.parentalControlParent.setLayoutManager(new LinearLayoutManager(this));
        mAddapter = new ParentalControlAddapter(this, mStore);
        binding.parentalControlParent.setAdapter(mAddapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.parental_control_menu, menu);
        MenuItem switshMenuItem = menu.findItem(R.id.app_bar_switch);
        parentalControl = Objects.requireNonNull(switshMenuItem.getActionView()).findViewById(R.id.switchView);
        parentalControl.setOnCheckedChangeListener(
                (compoundButton, b) -> {
                    mAddapter.setEnabled(b);
                    ParentalControl.setEnabled(mStore, b);
                });
        parentalControl.setOnClickListener(view -> {
            if (parentalControl.isChecked()) {
                parentalControl.setChecked(false);
                mAlertDialog.show();
            }
        });
        parentalControl.setChecked(ParentalControl.isEnabled(mStore));
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

    /** A dialog with the four-digit PIN field; {@code onPin} runs on the confirm button and on the fourth digit. */
    private AlertDialog pinDialog(int title, boolean cancelable, OnPin onPin) {
        XmlPullParser parser = getResources().getXml(R.xml.pin_entry);
        try {
            parser.next();
            parser.nextTag();
        } catch (Exception e) {
            Log.d("Error", String.valueOf(e.getMessage()));
        }
        AttributeSet attr = Xml.asAttributeSet(parser);
        PinEntryEditText editText = new PinEntryEditText(this, attr);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setCancelable(cancelable)
                .setView(editText)
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            Objects.requireNonNull(editText.getText()).clear();
            editText.requestFocus();
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setEnabled(false);
            b.setOnClickListener(view -> onPin.onPin(Objects.requireNonNull(editText.getText()).toString(), editText));
            if (!cancelable) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> finish());
            }
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
        editText.setOnPinEnteredListener(str -> onPin.onPin(str.toString(), editText));
        return dialog;
    }

    void showDialog() {
        final AlertDialog[] holder = new AlertDialog[1];
        holder[0] = pinDialog(R.string.enter_password, false, (pin, field) -> {
            ParentalControl.Verdict verdict = ParentalControl.verifyPin(this, pin);
            if (verdict.result == ParentalControl.Result.OK) {
                isShowDialog = true;
                holder[0].cancel();
            } else {
                showToast(pinMessage(this, verdict));
                Objects.requireNonNull(field.getText()).clear();
            }
        });
        holder[0].show();
    }

    /** The message for a refused PIN: wrong, or locked for the remaining seconds. */
    public static String pinMessage(@NonNull android.content.Context context, @NonNull ParentalControl.Verdict verdict) {
        if (verdict.result == ParentalControl.Result.LOCKED) {
            return context.getString(R.string.pin_locked, (verdict.lockedForMs + 999) / 1000);
        }
        return context.getString(R.string.incorect_password);
    }

    private void showToast(@NonNull String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
