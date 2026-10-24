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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
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
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.DownloadAdapter;
import com.fanok.audiobooks.databinding.ActivityClearSavedBinding;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.interface_pacatge.clearSeved.ClearSavedView;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.presenter.ClearSavedPresenter;
import com.r0adkll.slidr.Slidr;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class ClearSavedActivity extends MvpAppCompatActivity implements ClearSavedView {
    private ActivityClearSavedBinding binding;
    @InjectPresenter
    ClearSavedPresenter mPresenter;

    private boolean isReceiverRegistered = false;

    @Override
    protected void onStart() {
        super.onStart();
        if (!isReceiverRegistered) {
            // Only this app announces download changes; listened to while visible (UI-13).
            ContextCompat.registerReceiver(this, updateAdapter, new IntentFilter(Broadcast_UPDATE_ADAPTER),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            isReceiverRegistered = true;
        }
        // live speed, bytes and reasons of the rows in flight, every second while something downloads
        DownloadMonitor.get().addListener(liveStatus);
    }

    /** Back from another screen: transfers that ended meanwhile are no longer live, so the rows are read again. */
    @Override
    protected void onRestart() {
        super.onRestart();
        mPresenter.updateData();
    }

    @Override
    protected void onStop() {
        DownloadMonitor.get().removeListener(liveStatus);
        if (isReceiverRegistered) {
            unregisterReceiver(updateAdapter);
            isReceiverRegistered = false;
        }
        super.onStop();
    }

    private DownloadAdapter mAdapter;

    private final DownloadMonitor.Listener liveStatus = () -> {
        if (mAdapter != null) mAdapter.refreshLive();
    };
    private static final int MENU_EXPORT = Menu.FIRST + 100;



    @ProvidePresenter
    ClearSavedPresenter provideBookPresenter() {
        return new ClearSavedPresenter(getApplicationContext());
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

    @OptIn(markerClass = UnstableApi.class)
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

        DownloadStorageHelper.scanAndImportDownloadedBooks(getApplicationContext());


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
            public void onRestartClick(DownloadItem item) {
                mPresenter.restart(item);
            }

            @Override
            public void onPauseClick(DownloadItem item) {
                mPresenter.pause(item);
            }

            @Override
            public void onResumeClick(DownloadItem item) {
                mPresenter.resume(item);
            }

            @Override
            public void onOpenClick(DownloadItem item) {
                if (item != null) {
                    com.fanok.audiobooks.model.BooksDBModel dbModel = new com.fanok.audiobooks.model.BooksDBModel(ClearSavedActivity.this);
                    com.fanok.audiobooks.pojo.BookPOJO book = dbModel.getSomewhere(item.getBookUrl());
                    dbModel.closeDB();
                    if (book != null) {
                        BookActivity.startNewActivity(ClearSavedActivity.this, book);
                    }
                }
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
                DownloadItem item = mAdapter.getItem(viewHolder.getBindingAdapterPosition());
                if (item != null) {
                    showDeleteConfirmationDialog(item);
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

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

    }

    private final BroadcastReceiver updateAdapter = new BroadcastReceiver() {
        @OptIn(markerClass = UnstableApi.class)
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
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void updateAdapter() {
        mPresenter.updateData();
    }

    /** The swiped row is resolved when it is swiped; OK deletes exactly that book even if the list changed since. */
    private void showDeleteConfirmationDialog(@NonNull DownloadItem item) {
        final List<DownloadItem> chosen = Collections.singletonList(item);
        String name = item.getBookName() != null ? item.getBookName() : "";
        String message = name.isEmpty() ? "Удалить эту загрузку?" : "Удалить загрузку \"" + name + "\"?";
        new AlertDialog.Builder(this)
                .setTitle("Удаление загрузки")
                .setMessage(message)
                .setPositiveButton("ОК", (dialog, which) -> mPresenter.remove(chosen))
                .setNegativeButton("Отмена", (dialog, which) -> mAdapter.restore(item))
                .setOnCancelListener(dialog -> mAdapter.restore(item))
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.download_selection_menu, menu);
        menu.add(Menu.NONE, MENU_EXPORT, Menu.NONE, R.string.export_to_folder);
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
        MenuItem export = menu.findItem(MENU_EXPORT);
        if (export != null && mAdapter != null) {
            export.setVisible(mAdapter.getSelectedItemsSize() > 0);
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

        if (item.getItemId() == MENU_EXPORT && mAdapter != null) {
            List<DownloadItem> chosen = mAdapter.getSelectedItems();
            mAdapter.clearSelection();
            mPresenter.export(chosen);
        }

        if (item.getItemId() == R.id.action_delete_selected) {
            // Resolved now into an immutable list: a refresh while the dialog is open cannot change what OK deletes.
            final List<DownloadItem> chosen = mAdapter.getSelectedItems();
            if (!chosen.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Удаление загрузок")
                        .setMessage("Удалить выбранные загрузки (" + chosen.size() + ")?")
                        .setPositiveButton("ОК", (dialog, which) -> {
                            mPresenter.remove(chosen);
                            mAdapter.clearSelection();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showData(@NonNull List<DownloadItem> downloads) {
        mAdapter.submit(downloads);
    }

    @Override
    public void showMessage(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(this, getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showLogsDialog(String logs) {
        new AlertDialog.Builder(this)
                .setTitle("Логи удаления файлов")
                .setMessage(logs != null ? logs : "Логи пусты")
                .setPositiveButton("ОК", null)
                .show();
    }
}