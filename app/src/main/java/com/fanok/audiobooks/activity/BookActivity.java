package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ShareCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.arellomobile.mvp.MvpAppCompatActivity;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.SectionsPagerAdapter;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.google.gson.GsonBuilder;

public class BookActivity extends MvpAppCompatActivity implements Activity {

    private static final String TAG = "BookActivity";

    private static final String ARG_BOOK = "arg_book";
    @InjectPresenter
    BookPresenter mPresenter;
    private TabLayout tabs;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private BookPOJO mBookPOJO;
    private BottomSheetBehavior bottomSheetBehavior;

    public static void startNewActivity(@NonNull Context context, @NonNull BookPOJO bookPOJO) {
        Intent intent = new Intent(context, BookActivity.class);
        String json = new GsonBuilder().serializeNulls().create().toJson(bookPOJO);
        intent.putExtra(ARG_BOOK, json);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: called");
        Intent intent = getIntent();

        String json = intent.getStringExtra(ARG_BOOK);
        if (json == null) throw new NullPointerException();

        mBookPOJO = BookPOJO.parceJsonToBookPojo(json);
        setContentView(R.layout.activity_book);
        sectionsPagerAdapter = new SectionsPagerAdapter(this,
                getSupportFragmentManager(), mBookPOJO.getUrl());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        tabs = findViewById(R.id.tabs);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        tabs.setupWithViewPager(viewPager);

        View llBottomSheet = findViewById(R.id.player);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        View topBarButtonsControl = llBottomSheet.findViewById(R.id.topButtonsControls);
        ImageButton buttonCollapse = findViewById(R.id.buttonCollapse);
        buttonCollapse.setVisibility(View.INVISIBLE);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView nameCurent = findViewById(R.id.name_curent);


        bottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);


        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (actionBar != null) {
                    if (BottomSheetBehavior.STATE_COLLAPSED == newState) {
                        if (topBarButtonsControl.getVisibility() != View.VISIBLE) {
                            topBarButtonsControl.setVisibility(View.VISIBLE);
                        }
                        if (progressBar.getVisibility() != View.VISIBLE) {
                            progressBar.setVisibility(
                                    View.VISIBLE);
                        }
                        if (buttonCollapse.getVisibility() != View.INVISIBLE) {
                            buttonCollapse.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                if (BottomSheetBehavior.STATE_EXPANDED == newState) {
                    if (topBarButtonsControl.getVisibility() != View.INVISIBLE) {
                        topBarButtonsControl.setVisibility(View.INVISIBLE);
                    }
                    if (progressBar.getVisibility() != View.INVISIBLE) {
                        progressBar.setVisibility(
                                View.INVISIBLE);
                    }
                    if (buttonCollapse.getVisibility() != View.VISIBLE) {
                        buttonCollapse.setVisibility(View.VISIBLE);
                    }
                } else {
                    topBarButtonsControl.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                }
            }


            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                float alpha = 1 - slideOffset * 2;
                topBarButtonsControl.animate().alpha(alpha).setDuration(0);
                progressBar.animate().alpha(alpha).setDuration(0);
                nameCurent.animate().alpha(alpha).setDuration(0);
                if (alpha == 0.0) {
                    topBarButtonsControl.setVisibility(View.INVISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                } else {
                    topBarButtonsControl.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                }

                if (slideOffset > Consts.COLLAPS_BUTTON_VISIBLE) {
                    if (buttonCollapse.getVisibility() != View.VISIBLE) {
                        buttonCollapse.setVisibility(View.VISIBLE);
                    }

                    double alphaCollapse = (slideOffset - Consts.COLLAPS_BUTTON_VISIBLE)
                            / Consts.COLLAPS_BUTTON_VISIBLE_STEP;
                    buttonCollapse.animate().alpha((float) alphaCollapse).setDuration(0);
                    nameCurent.animate().alpha((float) alphaCollapse).setDuration(0);
                } else if (buttonCollapse.getVisibility() != View.INVISIBLE) {
                    buttonCollapse.setVisibility(View.INVISIBLE);
                }
            }
        });

        llBottomSheet.setOnClickListener(
                view -> {
                    if (BottomSheetBehavior.STATE_COLLAPSED == bottomSheetBehavior.getState()) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    }
                });

        buttonCollapse.setOnClickListener(view -> {
            if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState()) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });


        if (savedInstanceState == null) mPresenter.onCreate(mBookPOJO, this);

    }

    public void showSiries() {
        if (tabs.getTabCount() <= 2) {
            tabs.addTab(tabs.newTab().setText(getResources().getString(R.string.tab_text_3)));
            sectionsPagerAdapter.addTabPage(getResources().getString(R.string.tab_text_3));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        mPresenter.onOptionsMenuItemSelected(menuItem);
        return (super.onOptionsItemSelected(menuItem));
    }

    @Override
    protected void onDestroy() {
        mPresenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.book_activity_options_menu, menu);
        mPresenter.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public void setTabPostion(int postion) {
        if (postion < tabs.getTabCount() && postion > 0) {
            TabLayout.Tab tab = tabs.getTabAt(postion);
            if (tab != null) {
                tab.select();
            }
        }
    }

    @Override
    public void refreshActivity() {
        finish();
        Intent intent = new Intent(this, BookActivity.class);
        String json = new GsonBuilder().serializeNulls().create().toJson(mBookPOJO);
        intent.putExtra(ARG_BOOK, json);
        overridePendingTransition(0, 0);
        BookActivity.startNewActivity(this, mBookPOJO);
    }

    @Override
    public void shareTextUrl() {
        ShareCompat.IntentBuilder.from(this)
                .setType("text/plain")
                .setText(mBookPOJO.getUrl())
                .startChooser();
    }


    @Override
    public void addToMainScreen() {
        Log.d(TAG, "addToMainScreen: callded");
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }
}