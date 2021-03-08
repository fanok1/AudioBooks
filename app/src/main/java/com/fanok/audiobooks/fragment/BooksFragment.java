package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.MODEL_ARTIST;
import static com.fanok.audiobooks.Consts.MODEL_AUTOR;
import static com.fanok.audiobooks.Consts.MODEL_BOOKS;
import static com.fanok.audiobooks.Consts.MODEL_GENRE;
import static com.fanok.audiobooks.Consts.REQEST_CODE_SEARCH;
import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.arellomobile.mvp.MvpAppCompatFragment;
import com.arellomobile.mvp.presenter.InjectPresenter;
import com.arellomobile.mvp.presenter.ProvidePresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.GridSpacingItemDecoration;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.MainActivity;
import com.fanok.audiobooks.activity.SearchableActivity;
import com.fanok.audiobooks.adapter.BooksListAddapter;
import com.fanok.audiobooks.adapter.GenreListAddapter;
import com.fanok.audiobooks.databinding.FragmentBooksBinding;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.presenter.BooksPresenter;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


public class BooksFragment extends MvpAppCompatFragment implements BooksView {
    private static final String TAG = "BooksFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_TITLE = "title";
    private static final String ARG_SUB_TITLE = "sub_title";
    private static final String ARG_SUB_TITLE_STRING = "sub_title_string";
    private static final String ARG_MODEL = "model_id";


    @InjectPresenter
    BooksPresenter mPresenter;

    private BooksListAddapter mAddapterBooks;

    private GenreListAddapter mAddapterGenre;

    private FragmentBooksBinding binding;

    private int titleId;
    private int subTitleId;
    private String subTitleString;
    private int modelID;
    private String mUrl;


    @ProvidePresenter
    BooksPresenter provideBookPresenter() {
        Bundle arg = getArguments();
        String url = "";
        if (arg != null) {
            url = arg.getString(ARG_URL, "");
            titleId = arg.getInt(ARG_TITLE, 0);
            subTitleId = arg.getInt(ARG_SUB_TITLE, 0);
            subTitleString = arg.getString(ARG_SUB_TITLE_STRING, "");
            modelID = arg.getInt(ARG_MODEL, -1);
        }
        if (url.isEmpty()) throw new IllegalArgumentException("Variable 'url' contains not url");
        if (modelID == -1) throw new IllegalArgumentException("Illegal model id");
        mUrl = url;
        String subTitle = "";
        if (!subTitleString.isEmpty()) {
            subTitle = subTitleString;
        } else if (subTitleId != 0) subTitle = getResources().getString(subTitleId);
        return new BooksPresenter(url, modelID, subTitle,
                Objects.requireNonNull(getContext()).getApplicationContext());
    }

    public static BooksFragment newInstance(@NonNull String url, int title, int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, int subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_SUB_TITLE, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    public static BooksFragment newInstance(@NonNull String url, int title, String subTitle,
            int modelID) {
        BooksFragment fragment = new BooksFragment();
        if (!Consts.REGEXP_URL.matcher(url).matches()) {
            throw new IllegalArgumentException(
                    "Variable 'url' contains not url");
        }
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_SUB_TITLE_STRING, subTitle);
        args.putInt(ARG_MODEL, modelID);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        Bundle arg = getArguments();
        String url = "";
        if (arg != null) {
            url = arg.getString(ARG_URL, "");
            titleId = arg.getInt(ARG_TITLE, 0);
            subTitleId = arg.getInt(ARG_SUB_TITLE, 0);
            subTitleString = arg.getString(ARG_SUB_TITLE_STRING, "");
            modelID = arg.getInt(ARG_MODEL, -1);
        }
        if (url.isEmpty()) throw new IllegalArgumentException("Variable 'url' contains not url");
        if (modelID == -1) throw new IllegalArgumentException("Illegal model id");
        mUrl = url;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = FragmentBooksBinding.inflate(inflater, container, false);

        if (titleId != 0) {
            Objects.requireNonNull(getActivity()).setTitle(titleId);
        }
        ActionBar toolbar = ((AppCompatActivity) Objects.requireNonNull(
                getActivity())).getSupportActionBar();
        if (toolbar != null) {
            if (!subTitleString.isEmpty()) {
                toolbar.setSubtitle(subTitleString);
            } else if (subTitleId != 0) {
                toolbar.setSubtitle(subTitleId);
            } else {
                toolbar.setSubtitle("");
            }
        }

        switch (modelID) {
            case Consts.MODEL_BOOKS:
                mAddapterBooks = new BooksListAddapter();
                binding.list.setAdapter(mAddapterBooks);
                break;
            case Consts.MODEL_GENRE:
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                mAddapterGenre = new GenreListAddapter();
                binding.list.setAdapter(mAddapterGenre);
                break;
        }
        setHasOptionsMenu(true);

        binding.refresh.setOnRefreshListener(() -> getPresenter().onRefresh());

        binding.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int lastCompletelyVisibleItemPosition =
                        ((LinearLayoutManager) Objects.requireNonNull(
                                recyclerView.getLayoutManager())).findLastVisibleItemPosition();
                if (modelID != Consts.MODEL_GENRE
                        && lastCompletelyVisibleItemPosition > getCount() - 15 && dy > 0) {
                    getPresenter().loadBoks();
                }
            }
        });

        if (mAddapterGenre != null) {
            mAddapterGenre.setClickListner(
                    (view1, position) -> mPresenter.onGenreItemClick(view1, position));
        }

        if (mAddapterBooks != null) {
            mAddapterBooks.setListener(this::onItemSelected);

            mAddapterBooks.setLongListener(
                    (view13, position) -> mPresenter.onBookItemLongClick(view13, position,
                            getLayoutInflater()));
        }

        int orientation = this.getResources().getConfiguration().orientation;

        int isTablet = getResources().getInteger(R.integer.isTablet);

        if (isTablet == 0) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager();
            } else {
                setLayoutManager(2);
            }
        }
        if (isTablet > 0) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setLayoutManager(2);
            } else {
                setLayoutManager(3);
            }
        }

        return binding.getRoot();
    }


    private int getCount() {
        if (mAddapterBooks != null) {
            return mAddapterBooks.getItemCount();
        } else if (mAddapterGenre != null) {
            return mAddapterGenre.getItemCount();
        } else {
            return 0;
        }
    }

    @Override
    public void onDestroyView() {
        getPresenter().onDestroy();
        if (mAddapterBooks != null) {
            mAddapterBooks.close();
        }
        mAddapterBooks = null;
        mAddapterGenre = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater inflater) {
        if (mUrl.contains("izib.uk") || mUrl.contains("audiobook-mp3.com")) {
            inflater.inflate(R.menu.books_izibuk_options_menu, menu);
        } else {
            inflater.inflate(R.menu.books_options_menu, menu);
        }

        if (modelID == Consts.MODEL_BOOKS) {

            if (mUrl.contains("reader") || mUrl.contains("author") || mUrl.contains("genre")
                    || mUrl.contains("serie") || mUrl.contains("performer")) {
                menu.findItem(R.id.source).setVisible(false);
            } else {
                setColorPrimeriTextInIconItemMenu(
                        menu.findItem(R.id.source), Objects.requireNonNull(getContext()));
            }

            if (mUrl.contains("reader") || mUrl.contains("author") ||
                    mUrl.contains("serie") ||
                    (mUrl.contains("audiobook-mp3.com") && mUrl.contains("genre")) ||
                    (mUrl.contains("izib.uk") && mUrl.contains("genre"))) {
                menu.findItem(R.id.order).setVisible(false);
            } else {
                setColorPrimeriTextInIconItemMenu(
                        menu.findItem(R.id.order), Objects.requireNonNull(getContext()));
            }
        } else {
            menu.findItem(R.id.order).setVisible(false);
            menu.findItem(R.id.source).setVisible(false);
        }
        setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.app_bar_search),
                Objects.requireNonNull(getContext()));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void recreate() {
        Intent mStartActivity = new Intent(getContext(), MainActivity.class);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(getContext(), mPendingIntentId,
                mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) Objects.requireNonNull(getContext())
                .getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    @Override
    public void setLayoutManager() {
        binding.list.setLayoutManager(new LinearLayoutManager(this.getContext()));
        setItemDecoration(1);
    }

    @Override
    public void showData(@NonNull ArrayList bookPOJOS) {
        try {
            if (bookPOJOS.size() != 0) {
                if (bookPOJOS.get(0) instanceof BookPOJO) {
                    mAddapterBooks.setItem(bookPOJOS);
                } else if (bookPOJOS.get(0) instanceof GenrePOJO) {
                    mAddapterGenre.setItem(bookPOJOS);
                }
            }
        } catch (NullPointerException e) {
            showToast(R.string.error_display_data);
        }
    }


    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
        if (mAddapterGenre != null) mAddapterGenre.clearItem();
    }

    @Override
    public void setLayoutManager(int count) {
        binding.list.setLayoutManager(new GridLayoutManager(this.getContext(), count));
        setItemDecoration(count);
    }

    @Override
    public void showToast(int message) {
        Toast.makeText(this.getContext(), getResources().getText(message),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this.getContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void setPosition(int position) {
        Objects.requireNonNull(binding.list.getLayoutManager()).scrollToPosition(position);
    }

    @Override
    public void showProgres(boolean b) {
        if (b) {
            binding.mProgressBarLayout.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.mProgressBarLayout.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showFragment(fragment, tag);
        }
    }

    @Override
    public void showSearchActivity(int modelId) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.putExtra(Consts.ARG_MODEL, modelId);
        startActivityForResult(intent, REQEST_CODE_SEARCH);
    }

    @Override
    public void showRefreshing(boolean b) {
        binding.refresh.setRefreshing(b);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        getPresenter().onOptionItemSelected(item.getItemId());
        return true;
    }

    protected BooksPresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            NavigationView navigationView = activity.getNavigationView();
            ArrayList<TextView> mTextViewArrayList = activity.getTextViewArrayList();
            final TypedValue SelectedValue = new TypedValue();
            activity.getTheme().resolveAttribute(R.attr.mySelectableItemBackground, SelectedValue,
                    true);
            switch (modelID) {
                case MODEL_BOOKS:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_audiobooks);
                    } else if (mTextViewArrayList != null && !mTextViewArrayList.isEmpty()) {
                        mTextViewArrayList.get(0).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_GENRE:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_genre);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 1) {
                        mTextViewArrayList.get(1).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_AUTOR:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_autor);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 2) {
                        mTextViewArrayList.get(2).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case MODEL_ARTIST:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_artist);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 3) {
                        mTextViewArrayList.get(3).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Consts.REQEST_CODE_SEARCH && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                mPresenter.onActivityResult(data);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void showBooksActivity(@NotNull @NonNull BookPOJO bookPOJO) {
        BookActivity.startNewActivity(Objects.requireNonNull(getContext()), bookPOJO);
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        binding.list.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    private void onItemSelected(View view12, int position) {
        mPresenter.onBookItemClick(view12, position);
    }
}
