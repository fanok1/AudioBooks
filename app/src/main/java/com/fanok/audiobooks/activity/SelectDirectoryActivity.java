package com.fanok.audiobooks.activity;

import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.R;
import com.google.android.material.textfield.TextInputEditText;
import com.r0adkll.slidr.Slidr;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SelectDirectoryActivity extends AppCompatActivity {

    @BindView(R.id.textInputEditText)
    TextInputEditText mTextInputEditText;
    boolean selection = false;
    @BindView(R.id.button)
    Button mButton;

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

        String path = pref.getString("pref_dowland_path", "");

        if (!path.isEmpty()) {
            mTextInputEditText.setText(path);
        } else {
            mTextInputEditText.setText(new ContextWrapper(this).getFilesDir().toString());
        }

        mButton.setOnClickListener(view -> mTextInputEditText.setText(new ContextWrapper(
                view.getContext()).getFilesDir().toString()));


        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_NoActionBar);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_NoActionBar);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void click(View view) {
        if (!selection) {
            selection = true;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.select_folder_description)),
                    251);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 251) {
            selection = false;
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    mTextInputEditText.setText(uri.getPath());
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.confirm) {
            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("pref_dowland_path",
                    Objects.requireNonNull(
                            Objects.requireNonNull(mTextInputEditText.getText()).toString()));
            editor.apply();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.confirm_options_menu, menu);
        return true;
    }


}

