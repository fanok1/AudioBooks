package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;

import butterknife.BindView;
import butterknife.ButterKnife;

public class PopupGetPlus extends AppCompatActivity {
    @BindView(R.id.ib_close)
    ImageButton mIbClose;
    @BindView(R.id.title)
    TextView mTitle;
    @BindView(R.id.subTitle)
    TextView mSubTitle;
    @BindView(R.id.buy)
    LinearLayout mBuy;
    @BindView(R.id.linearLayout)
    LinearLayout mLinearLayout;
    @BindView(R.id.coordinator)
    CoordinatorLayout mCoordinator;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plus_version_info);
        ButterKnife.bind(this);

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_Popup);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_Popup);
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;


        ViewGroup.LayoutParams layoutParams = mLinearLayout.getLayoutParams();
        layoutParams.height = (int) (height * .8);
        layoutParams.width = (int) (width * .8);
        mLinearLayout.setLayoutParams(layoutParams);
        mLinearLayout.setOnClickListener(null);

        mIbClose.setOnClickListener(view -> finish());
        mCoordinator.setOnClickListener(view -> finish());

        mTitle.setText(R.string.getPlusTitle);
        mSubTitle.setText(R.string.getPlusSubTitle);

        mBuy.setOnClickListener(
                view -> Toast.makeText(view.getContext(), "Данная функция находиться в разработке",
                        Toast.LENGTH_SHORT).show());
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
        }


        return theme;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
