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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.PlusVersionInfoBinding;
import com.fanok.audiobooks.pojo.StorageAds;


public class PopupGetPlus extends AppCompatActivity {

    public static final String Broadcast_RECREATE = "recreate";

    private PlusVersionInfoBinding binding;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
    }

    private final BroadcastReceiver recreate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recreate();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = PlusVersionInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        register_recreate();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            setTheme(R.style.AppTheme_Popup);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            setTheme(R.style.LightAppTheme_Popup);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            setTheme(R.style.AppThemeBlack_Popup);
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        ViewGroup.LayoutParams layoutParams = binding.linearLayout.getLayoutParams();
        layoutParams.height = (int) (height * .8);
        layoutParams.width = (int) (width * .8);
        binding.linearLayout.setLayoutParams(layoutParams);
        binding.linearLayout.setOnClickListener(null);

        binding.ibClose.setOnClickListener(view -> finish());
        binding.coordinator.setOnClickListener(view -> finish());

        if (!StorageAds.idDisableAds()) {
            binding.title.setText(R.string.getPlusTitle);
            binding.subTitle.setText(R.string.getPlusSubTitle);
            binding.buttonTitle.setText(R.string.buy);
            binding.buttonSubTitle.setText(R.string.price);
            binding.buy.setOnClickListener(view -> {
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
            binding.title.setText(R.string.congratulations);
            binding.subTitle.setText(R.string.plusSubTitle);
            binding.buttonTitle.setText(R.string.good);
            binding.buttonSubTitle.setVisibility(View.GONE);
            binding.buy.setOnClickListener(view -> finish());
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
