package com.fanok.audiobooks.interface_pacatge;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

public interface MainContract {
    interface View {
        void openActivity(@NonNull Intent intent);

        void showFragment(@NonNull Fragment fragment, String tag);
    }

    interface Presenter {
        void onItemSelected(@NonNull MenuItem item);

        void onDestroy();

        void onCreate();
    }
}
