package com.fanok.audiobooks.activity;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.LocaleManager;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.DownloadAdapter;
import com.fanok.audiobooks.databinding.ActivityClearSavedBinding;
import com.fanok.audiobooks.interface_pacatge.clearSeved.ClearSavedView;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.presenter.ClearSavedPresenter;
import com.r0adkll.slidr.Slidr;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class ClearSavedActivity extends MvpAppCompatActivity implements ClearSavedView {
    private ActivityClearSavedBinding binding;
    @InjectPresenter
    ClearSavedPresenter mPresenter;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateAdapter);
        mPresenter.onDestroy();
    }

    private DownloadAdapter mAdapter;



    @ProvidePresenter
    ClearSavedPresenter provideBookPresenter() {
        return new ClearSavedPresenter(getApplicationContext());
    }


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.onAttach(base));
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

    public static void startActivity(@NotNull Activity activity) {
        Intent intent = new Intent(activity, ClearSavedActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.enter, R.anim.exit);
    }

    @SuppressLint({"UnsafeOptInUsageError", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClearSavedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Slidr.attach(this);
        setSupportActionBar(binding.toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


        OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
        dispatcher.addCallback(this, new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left);
            }
        });

        RecyclerView recyclerView = binding.list;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new DownloadAdapter(new DownloadAdapter.OnDownloadControlClickListener() {
            @Override
            public void onRestartClick(DownloadItem item, int position) {
                mPresenter.restart(item.getFileName());
            }

            @Override
            public void onPauseClick(DownloadItem item, int position) {
                mPresenter.pause(item.getFileName());
            }

            @Override
            public void onResumeClick(DownloadItem item, int position) {
                mPresenter.resume(item.getFileName());
            }
        });

        mAdapter.setOnItemSelectionListener(this::invalidateOptionsMenu);
        recyclerView.setAdapter(mAdapter);

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                final int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    showDeleteConfirmationDialog(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                new RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        .addBackgroundColor(ContextCompat.getColor(ClearSavedActivity.this, android.R.color.holo_red_dark))
                        .addActionIcon(R.drawable.ic_delete_swipe)
                        .create()
                        .decorate();

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateAdapter, new IntentFilter(Broadcast_UPDATE_ADAPTER), RECEIVER_EXPORTED);
        }else {
            registerReceiver(updateAdapter, new IntentFilter(Broadcast_UPDATE_ADAPTER));
        }

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

    }

    private final BroadcastReceiver updateAdapter = new BroadcastReceiver() {
        @UnstableApi
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAdapter();
        }
    };

    @Override
    public void showProgress(boolean isProgress) {
        if (isProgress) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    @UnstableApi
    @Override
    public void updateAdapter() {
        mPresenter.updateData();
    }

    private void showDeleteConfirmationDialog(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Подтверждение")
                .setMessage("Вы уверены, что хотите удалить этот файл?")
                .setPositiveButton("Да", (dialog, which) -> {
                    mPresenter.remove(position);
                    mAdapter.notifyItemRemoved(position);
                })
                .setNegativeButton("Нет", (dialog, which) -> mAdapter.notifyItemChanged(position))
                .setOnCancelListener(dialog -> mAdapter.notifyItemChanged(position))
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.download_selection_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem checkboxItem = menu.findItem(R.id.action_checkbox);
        MenuItem delete = menu.findItem(R.id.action_delete_selected);
        if (checkboxItem != null && delete!=null && mAdapter!=null) {
            checkboxItem.setVisible(mAdapter.getSelectedItemsSize()>0);
            delete.setVisible(mAdapter.getSelectedItemsSize()>0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.left_to_right, R.anim.right_to_left);
        }

        if (item.getItemId() == R.id.action_checkbox) {
            if(mAdapter!=null) {
                if (mAdapter.getSelectedItemsSize() == mAdapter.getItemCount()) {
                    mAdapter.clearSelection();
                    item.setChecked(false);
                } else {
                    mAdapter.selectAll();
                    item.setChecked(true);
                }
            }
        }

        if(item.getItemId()==R.id.action_delete_selected){
            final SparseBooleanArray selectedItems = mAdapter.getSelectedItems();
            ArrayList<DownloadItem> arrayList = new ArrayList<>();
            for (int i = 0; i < selectedItems.size(); i++) {
                arrayList.add(mAdapter.getItem(selectedItems.keyAt(i)));

            }
            mPresenter.remove(arrayList);
            mAdapter.clearSelection();
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showData(@NonNull ArrayList<DownloadItem> downloads) {
        ArrayList<DownloadItem> arrayList = new ArrayList<>();
        for (DownloadItem item : downloads) {
            arrayList.add(new DownloadItem(item));
        }


        if(mAdapter.getItemCount()>0){
            mAdapter.updateItems(new ArrayList<>(arrayList));
        }else {
            mAdapter.setItemList(new ArrayList<>(arrayList));
        }
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(this, getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }
}