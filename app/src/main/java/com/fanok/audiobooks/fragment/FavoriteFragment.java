package com.fanok.audiobooks.fragment;

import static com.fanok.audiobooks.Consts.REQEST_CODE_SEARCH;
import static com.fanok.audiobooks.Consts.TABLE_FAVORITE;
import static com.fanok.audiobooks.Consts.TABLE_HISTORY;
import static com.fanok.audiobooks.Consts.getAttributeColor;
import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
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
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import com.fanok.audiobooks.databinding.FragmentFavoriteBinding;
import com.fanok.audiobooks.interface_pacatge.favorite.FavoriteView;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.presenter.FavoritePresenter;
import com.google.android.material.navigation.NavigationView;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class FavoriteFragment extends MvpAppCompatFragment implements FavoriteView {
    private static final String TAG = "FavoriteFragment";
    private static final String ARG_TITLE = "title";
    private static final String ARG_TABLE = "table";

    @InjectPresenter
    FavoritePresenter mPresenter;

    private BooksListAddapter mAddapterBooks;

    private int titleId;

    private int table;

    private SearchView searchView;

    private FragmentFavoriteBinding binding;

    public static FavoriteFragment newInstance(int title, int table) {
        FavoriteFragment fragment = new FavoriteFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_TABLE, table);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: called");
        super.onCreate(savedInstanceState);
        Bundle arg = getArguments();
        if (arg != null) {
            titleId = arg.getInt(ARG_TITLE, 0);
            table = arg.getInt(ARG_TABLE, 0);
        }

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentFavoriteBinding.inflate(inflater, container, false);

        if (titleId != 0) {
            requireActivity().setTitle(titleId);
        }

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());

        mAddapterBooks = new BooksListAddapter(pref.getBoolean("history_procent", true));
        binding.list.setAdapter(mAddapterBooks);
        setHasOptionsMenu(true);
        mAddapterBooks.setListener(this::onItemSelected);

        mAddapterBooks.setLongListener(
                (view13, position) -> mPresenter.onBookItemLongClick(view13, position,
                        getLayoutInflater()));

        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                mPresenter.onRemove(position);
                Toast.makeText(getContext(), R.string.delete_complite, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                    int actionState, boolean isCurrentlyActive) {
                new RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY,
                        actionState, isCurrentlyActive)
                        .addBackgroundColor(getAttributeColor(requireContext(), R.attr.deleteColor))
                        .addActionIcon(R.drawable.ic_delete_swipe)
                        .setIconHorizontalMargin(TypedValue.COMPLEX_UNIT_DIP, 24)
                        .create()
                        .decorate();
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState,
                        isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(binding.list);


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

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.favorite_options_menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());

        Consts.setColorPrimeriTextInIconItemMenu(item, requireContext());

        if (pref.getBoolean("search_pref", false)) {
            item.setActionView(null);
            item.setOnMenuItemClickListener(menuItem -> {
                Intent intent = new Intent(getContext(), SearchableActivity.class);
                intent.putExtra(Consts.ARG_MODEL, Consts.MODEL_BOOKS);
                startActivityForResult(intent, REQEST_CODE_SEARCH);
                return true;
            });
        } else {
            searchView = (SearchView) item.getActionView();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String s) {
                    getPresenter().onSearch(s);
                    return false;
                }

                @Override
                public boolean onQueryTextSubmit(String s) {
                    return false;
                }
            });
        }

        if (table == TABLE_FAVORITE) {
            menu.findItem(R.id.order).setVisible(true);
            menu.findItem(R.id.filter).setVisible(true);

            setColorPrimeriTextInIconItemMenu(
                    menu.findItem(R.id.filter), requireContext());

            setColorPrimeriTextInIconItemMenu(
                    menu.findItem(R.id.order), requireContext());

            String sort = pref.getString("pref_sort_favorite", getString(R.string.sort_value_date));
            if (getString(R.string.sort_value_name).equals(sort)) {
                menu.findItem(R.id.name).setChecked(true);
            } else if (getString(R.string.sort_value_genre).equals(sort)) {
                menu.findItem(R.id.genre).setChecked(true);
            } else if (getString(R.string.sort_value_autor).equals(sort)) {
                menu.findItem(R.id.autor).setChecked(true);
            } else if (getString(R.string.sort_value_artist).equals(sort)) {
                menu.findItem(R.id.artist).setChecked(true);
            } else if (getString(R.string.sort_value_series).equals(sort)) {
                menu.findItem(R.id.series).setChecked(true);
            } else {
                menu.findItem(R.id.date).setChecked(true);
            }

        }

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public void onDestroyView() {
        getPresenter().onDestroy();
        if (mAddapterBooks != null) {
            mAddapterBooks.close();
        }
        mAddapterBooks = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        mPresenter.onOptionsItemSelected(binding.view, item.getItemId());
        item.setChecked(true);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void setLayoutManager(int count) {
        binding.list.setLayoutManager(new GridLayoutManager(this.getContext(), count));
        setItemDecoration(count);
    }

    @Override
    public void setLayoutManager() {
        binding.list.setLayoutManager(new LinearLayoutManager(this.getContext()));
        setItemDecoration(1);
    }

    @Override
    public void showData(@NonNull ArrayList<BookPOJO> bookPOJOS) {
        if (mAddapterBooks != null) {
            mAddapterBooks.setItem(bookPOJOS);
        }
    }

    @Override
    public void updateFilter() {
        if (searchView != null) {
            mPresenter.onSearch(searchView.getQuery().toString());
        }
    }


    @Override
    public void clearData() {
        if (mAddapterBooks != null) mAddapterBooks.clearItem();
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
    public void showProgres(boolean b) {
        if (b) {
            binding.progressBarLayout.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.progressBarLayout.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void setSubTitle(@NotNull String text) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            Objects.requireNonNull(mainActivity.getSupportActionBar()).setSubtitle(text);
        }
    }


    @Override
    public void showFragment(@NonNull Fragment fragment, @NonNull String tag) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            activity.showFragment(fragment, tag);
        }
    }

    protected FavoritePresenter getPresenter() {
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
            switch (table) {
                case TABLE_FAVORITE:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_favorite);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 4) {
                        mTextViewArrayList.get(4).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
                case TABLE_HISTORY:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_history);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 5) {
                        mTextViewArrayList.get(5).setBackgroundResource(SelectedValue.resourceId);
                    }
                    break;
            }
        }
        mPresenter.loadBooks();
    }

    @Override
    public void showBooksActivity(@NotNull @NonNull BookPOJO bookPOJO) {
        BookActivity.startNewActivity(requireContext(), bookPOJO);
    }

    private void setItemDecoration(int count) {
        int spacing = (int) getResources().getDimension(R.dimen.recycler_item_margin);
        boolean includeEdge = true;
        binding.list.addItemDecoration(new GridSpacingItemDecoration(count, spacing, includeEdge));
    }

    @ProvidePresenter
    FavoritePresenter provide() {
        Bundle arg = getArguments();
        if (arg != null) {
            table = arg.getInt(ARG_TABLE, 0);
        }
        return new FavoritePresenter(requireContext().getApplicationContext(),
                table);
    }

    @Override
    public void showSearchActivity(int modelId) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.putExtra(Consts.ARG_MODEL, modelId);
        startActivityForResult(intent, REQEST_CODE_SEARCH);
    }


    private void onItemSelected(View view12, int position) {
        mPresenter.onBookItemClick(view12, position);
    }
}