package com.fanok.audiobooks.activity;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.codekidlabs.storagechooser.Content;
import com.codekidlabs.storagechooser.StorageChooser;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.google.android.material.textfield.TextInputEditText;
import com.r0adkll.slidr.Slidr;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SelectDirectoryActivity extends AppCompatActivity {

    @BindView(R.id.textInputEditText)
    TextInputEditText mTextInputEditText;
    @BindView(R.id.button)
    Button mButton;


    private static final String TAG = "SelectDirectoryActivity";
    private static final int REQUEST_DIRECTORY = 165;
    private String oldPath;

    private StorageChooser chooser;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_directory);
        ButterKnife.bind(this);
        Slidr.attach(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        mTextInputEditText.setKeyListener(null);
        mTextInputEditText.setOnFocusChangeListener((view, b) -> click(view));
        mTextInputEditText.setOnClickListener(this::click);
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        oldPath = pref.getString("pref_downland_path",
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getPath());

        mTextInputEditText.setText(oldPath);
        mButton.setOnClickListener(view -> mTextInputEditText.setText(new ContextWrapper(
                view.getContext()).getFilesDir().toString()));

        Content content = new Content();
        content.setCancelLabel(getString(R.string.cancel));
        content.setSelectLabel(getString(R.string.select));
        content.setOverviewHeading(getString(R.string.select_folder));
        content.setCreateLabel(getString(R.string.create));
        content.setFolderCreatedToastText(getString(R.string.folder_created));
        content.setFolderErrorToastText(getString(R.string.error_folder_create));
        content.setNewFolderLabel(getString(R.string.new_folder));
        content.setTextfieldHintText(getString(R.string.new_folder));
        content.setTextfieldErrorText(getString(R.string.empty_text));

        StorageChooser.Builder builder = new StorageChooser.Builder()
                .withContent(content)
                .allowAddFolder(true)
                .withActivity(SelectDirectoryActivity.this)
                .withFragmentManager(getFragmentManager())
                .withMemoryBar(true)
                .withPredefinedPath(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).getPath())
                .allowCustomPath(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_light_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            StorageChooser.Theme theme = new StorageChooser.Theme(getApplicationContext());
            theme.setScheme(getResources().getIntArray(R.array.paranoid_theme));
            builder.setTheme(theme);
        }
        chooser = builder.build();


    }

    private void click(@NotNull View view) {
        boolean hasPermission = (ContextCompat.checkSelfPermission(
                view.getContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_DIRECTORY);
        } else {
            showDirPiker();
        }
    }

    private void showDirPiker() {
        chooser.setOnSelectListener(path -> {
            String s = path.substring(indexOf('/', path, 2) + 1, indexOf('/', path, 3));
            if (s.equals("emulated")) {
                mTextInputEditText.setText(path);
            } else {
                //String newPath = path.replace(s, "emulated/1");
                File file = new File(path);
                if (file.isDirectory() && file.canWrite()) {
                    mTextInputEditText.setText(path);
                } else {
                    Toast.makeText(this, R.string.not_can_writing, Toast.LENGTH_SHORT).show();
                }
            }
        });
        chooser.show();
    }

    private int indexOf(char c, String path, int pos) {
        int temp = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == c) {
                temp++;
                if (temp == pos) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        } else if (item.getItemId() == R.id.confirm) {
            save();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        if (mTextInputEditText.getText() != null) {
            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("pref_downland_path", mTextInputEditText.getText().toString());
            editor.apply();
            oldPath = mTextInputEditText.getText().toString();
        }
    }

    @Override
    public void onBackPressed() {

        if (mTextInputEditText.getText() != null && oldPath.equals(
                mTextInputEditText.getText().toString())) {
            super.onBackPressed();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.exit);
            builder.setMessage(R.string.exit_confirm);
            builder.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                if (mTextInputEditText.getText() != null) {
                    save();
                    finish();
                }
            });
            builder.setNegativeButton(R.string.no, (dialogInterface, i) -> finish());
            builder.setNeutralButton(R.string.cancel,
                    (dialogInterface, i) -> dialogInterface.cancel());
            builder.setCancelable(false);
            builder.show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.confirm_options_menu, menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions,
            @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_DIRECTORY) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        getString(R.string.worning_not_allowed_write_storege),
                        Toast.LENGTH_LONG).show();
            } else {
                showDirPiker();
            }
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
        }


        return theme;
    }


}

