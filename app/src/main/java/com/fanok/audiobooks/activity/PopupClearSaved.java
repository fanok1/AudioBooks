package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.ClearSavedAdapter;
import com.fanok.audiobooks.databinding.ActivityClearSavedPopupBinding;
import com.fanok.audiobooks.pojo.ClearSavedPOJO;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class PopupClearSaved extends AppCompatActivity {

    private static final String TAG = "PopupClearSaved";

    private ActivityClearSavedPopupBinding binding;


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClearSavedPopupBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_Popup2);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_Popup2);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_Popup2);
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        getWindow().setLayout((int) (width * 0.8), (int) (height * 0.8));

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        ClearSavedAdapter clearSavedAdapter = new ClearSavedAdapter();
        ArrayList<ClearSavedPOJO> list = new ArrayList<>();
        File[] folders = getExternalFilesDirs(null);
        for (int i = 0; i < folders.length; i++) {
            if (folders[i] != null) {
                File file = new File(folders[i].getAbsolutePath());
                if (file.exists() && file.isDirectory()) {
                    File[] dirs = file.listFiles();
                    if (dirs != null) {
                        for (File dir : dirs) {
                            if (dir.exists() && file.isDirectory()) {
                                String[] files = dir.list();
                                if (files != null) {
                                    if (files.length == 0) {
                                        dir.delete();
                                    } else {
                                        String storege = getString(R.string.dir_title_emulated);
                                        if (i == 0) {
                                            storege = getString(R.string.dir_title_emulated);
                                        } else if (i == 1) {
                                            storege = getString(R.string.dir_title_sdcrd);
                                        }
                                        list.add(new ClearSavedPOJO(dir, storege));
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        if (list.size() == 0) {
            Toast.makeText(this, R.string.saved_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }

        clearSavedAdapter.setData(list);

        int spacing = (int) getResources().getDimension(R.dimen.text_margin);
        binding.recyclerView.addItemDecoration(new GridSpacingItemDecoration(1, spacing, true));
        clearSavedAdapter.setChackedChange(() -> {
            binding.checkBoxAll.setChecked(
                    clearSavedAdapter.getItemCount() == clearSavedAdapter.getSelectedItemsSize());
        });
        binding.recyclerView.setAdapter(clearSavedAdapter);

        binding.checkBoxAll.setOnClickListener(view -> {
            if (binding.checkBoxAll.isChecked()) {
                clearSavedAdapter.setSelectedAll();
            } else {
                clearSavedAdapter.clearSelected();
            }
        });

        binding.button.setOnClickListener(view -> {
            HashSet<File> files = clearSavedAdapter.getSelectedItems();
            if (files.size() == 0) {
                Toast.makeText(this, R.string.no_selecetd, Toast.LENGTH_SHORT).show();
                return;
            }
            for (File file : files) {
                if (file.exists()) {
                    delete(file);
                }
            }
            recreate();
        });
    }

    private void delete(File file) {
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File value : files) {
                    delete(value);
                }
            } else {
                if (file.delete()) {
                    Log.d(TAG, "delete: true");
                } else {
                    Log.d(TAG, "delete: false");
                }

            }
        } else {
            if (file.delete()) {
                Log.d(TAG, "delete: true");
            } else {
                Log.d(TAG, "delete: false");
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
            theme.applyStyle(R.style.AppTheme_Popup, true);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            theme.applyStyle(R.style.LightAppTheme_Popup, true);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            theme.applyStyle(R.style.AppThemeBlack_Popup, true);
        }


        return theme;
    }
}
