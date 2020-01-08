package com.fanok.audiobooks.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.StorageAds;

import butterknife.BindView;
import butterknife.ButterKnife;

public class PopupGetPlus extends AppCompatActivity {

    public static final String Broadcast_RECREATE = "recreate";

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
    @BindView(R.id.buttonTitle)
    TextView mButtonTitle;
    @BindView(R.id.buttonSubTitle)
    TextView mButtonSubTitle;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    private BroadcastReceiver recreate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recreate();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.plus_version_info);
        ButterKnife.bind(this);

        register_recreate();

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

        if (!StorageAds.idDisableAds()) {
            mTitle.setText(R.string.getPlusTitle);
            mSubTitle.setText(R.string.getPlusSubTitle);
            mButtonTitle.setText(R.string.buy);
            mButtonSubTitle.setText(R.string.price);
            mBuy.setOnClickListener(view -> {
                //Billing.launchBilling(getActivity(), Consts.mSkuId);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Плюс версия");
                builder.setMessage(
                        "Гугл заблокировали возможность совершать покупки через приложение."
                                + "Как только мы разберемся из-за чего это произошло мы вернем "
                                + "возможность купить Plus версию приложения."
                                + "Извините за вызванные неудобства.\n"
                                + "С уважением, администрация приложения");
                builder.setIcon(R.drawable.ic_launcher_foreground);
                builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.cancel());
                builder.setCancelable(true);
                builder.show();
            });
        } else {
            mTitle.setText(R.string.congratulations);
            mSubTitle.setText(R.string.plusSubTitle);
            mButtonTitle.setText(R.string.good);
            mButtonSubTitle.setVisibility(View.GONE);
            mBuy.setOnClickListener(view -> finish());
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
        }


        return theme;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private Activity getActivity() {
        Context context = this;
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(recreate);
    }

    private void register_recreate() {
        IntentFilter filter = new IntentFilter(Broadcast_RECREATE);
        registerReceiver(recreate, filter);
    }
}
