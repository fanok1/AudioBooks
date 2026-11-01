package com.fanok.audiobooks.fragment;
import static com.fanok.audiobooks.Consts.TABLE_FAVORITE;
import static com.fanok.audiobooks.Consts.TABLE_HISTORY;
import static com.fanok.audiobooks.Consts.TABLE_SAVED;
import static com.fanok.audiobooks.Consts.getAttributeColor;
import static com.fanok.audiobooks.Consts.setColorPrimeriTextInIconItemMenu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
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
import com.fanok.audiobooks.policy.DefaultCover;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.presenter.FavoritePresenter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Picasso;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;
import java.util.ArrayList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class FavoriteFragment extends MvpAppCompatFragment implements FavoriteView {
    private static final String TAG = "FavoriteFragment";
    private static final String ARG_TITLE = "title";
    private static final String ARG_TABLE = "table";
    /** Seconds before "Yes" of the clear-all question can be pressed. */
    private static final int CLEAR_CONFIRM_DELAY_S = 3;

    @InjectPresenter
    FavoritePresenter mPresenter;

    private BooksListAddapter mAddapterBooks;
    /** The parental-control version the visible covers were bound with. */
    private long mParentalVersion;

    private int titleId;

    private int table;

    private SearchView searchView;

    private FragmentFavoriteBinding binding;

    private BottomSheetDialog bookMenu;

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
        mPresenter.onCreate(requireContext().getApplicationContext());

    }

    private final ActivityResultLauncher<Intent> searchActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.d(TAG, "onCreate: searchActivityLauncher");
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentFavoriteBinding.inflate(inflater, container, false);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.favorite_options_menu, menu);
                MenuItem item = menu.findItem(R.id.app_bar_search);
                SharedPreferences pref = PreferenceManager
                        .getDefaultSharedPreferences(requireContext());

                Consts.setColorPrimeriTextInIconItemMenu(item, requireContext());

                if (pref.getBoolean("search_pref", false)) {
                    item.setActionView(null);
                    item.setOnMenuItemClickListener(menuItem -> {
                        showSearchActivity(Consts.MODEL_BOOKS);
                        return true;
                    });
                } else {
                    searchView = (SearchView) item.getActionView();
                    Objects.requireNonNull(searchView).setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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


                if(table==TABLE_SAVED){
                    menu.findItem(R.id.saved).setVisible(false);
                    menu.findItem(R.id.saved_filter).setVisible(false);
                }

                if (table == TABLE_FAVORITE || table == TABLE_SAVED) {
                    menu.findItem(R.id.order).setVisible(true);
                    menu.findItem(R.id.filter).setVisible(true);
                    setColorPrimeriTextInIconItemMenu(
                            menu.findItem(R.id.order), requireContext());
                    String sort = pref.getString("pref_sort_favorite", getString(R.string.sort_value_date));
                    setColorPrimeriTextInIconItemMenu(
                            menu.findItem(R.id.filter), requireContext());

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
                    } else if (getString(R.string.sort_value_saved).equals(sort)&&table!=TABLE_SAVED){
                        menu.findItem(R.id.saved).setChecked(true);
                    }else {
                        menu.findItem(R.id.date).setChecked(true);
                    }
                }

                setColorPrimeriTextInIconItemMenu(menu.findItem(R.id.app_bar_search), requireContext());

                if (table == TABLE_FAVORITE || table == TABLE_HISTORY) {
                    MenuItem clear = menu.findItem(R.id.clear_list);
                    clear.setVisible(true);
                    setColorPrimeriTextInIconItemMenu(clear, requireContext());
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.clear_list) {
                    confirmClear();
                    return true;
                }
                mPresenter.onOptionsItemSelected(item.getItemId());
                item.setChecked(true);
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        if (titleId != 0) {
            requireActivity().setTitle(titleId);
        }
        setSubTitle(""); // the previous screen's subtitle; an active filter sets its own on view-state replay

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(requireContext());

        mAddapterBooks = new BooksListAddapter(pref.getBoolean("history_procent", true));
        binding.list.setAdapter(mAddapterBooks);
        mAddapterBooks.setListener((view, position) -> {
            if (position != RecyclerView.NO_POSITION) {
                mPresenter.onBookItemClick(position);
            }
        });

        mAddapterBooks.setLongListener((view, position) -> {
            if (position != RecyclerView.NO_POSITION) {
                mPresenter.onBookItemLongClick(position);
            }
        });

        binding.placeholder.placeholderRetry.setOnClickListener(v -> mPresenter.loadBooks());

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
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    mPresenter.onRemove(position);
                }
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
    public void onDestroyView() {
        if (bookMenu != null) {
            bookMenu.dismiss();
            bookMenu = null;
        }
        if (mAddapterBooks != null) {
            mAddapterBooks.close();
        }
        mAddapterBooks = null;
        searchView = null;
        super.onDestroyView();
        binding = null;
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
        if (binding != null) {
            showPlaceholder(bookPOJOS.isEmpty() ? R.string.list_empty : 0, false);
        }
    }

    /** The centred message instead of the list; {@code 0} hides it. */
    private void showPlaceholder(int message, boolean retry) {
        if (message == 0) {
            binding.placeholder.getRoot().setVisibility(View.GONE);
            return;
        }
        binding.placeholder.placeholderText.setText(message);
        binding.placeholder.placeholderRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        binding.placeholder.getRoot().setVisibility(View.VISIBLE);
    }

    @Override
    public void showError(boolean b) {
        if (binding == null) {
            return;
        }
        if (b) {
            showPlaceholder(R.string.error_load_data, true);
        } else if (binding.placeholder.placeholderRetry.getVisibility() == View.VISIBLE) {
            showPlaceholder(0, false);
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

    /**
     * Asks before emptying the list; "Yes" counts down {@link #CLEAR_CONFIRM_DELAY_S} seconds before it can be
     * pressed, so a stray tap on the toolbar icon clears nothing.
     */
    private void confirmClear() {
        final String yes = getString(R.string.yes);
        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(titleId != 0 ? titleId : R.string.app_name)
                .setMessage(table == TABLE_HISTORY ? R.string.confirm_clear_history : R.string.confirm_clear_favorite)
                .setPositiveButton(yes, (d, which) -> mPresenter.clearAll())
                .setNegativeButton(R.string.cancel, null)
                .show();
        final Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setEnabled(false);
        button.post(new Runnable() {
            private int left = CLEAR_CONFIRM_DELAY_S;

            @Override
            public void run() {
                if (!dialog.isShowing()) {
                    return;
                }
                if (left > 0) {
                    // the dialog's context: the fragment may have left the screen while the dialog stays
                    button.setText(dialog.getContext().getString(R.string.yes_countdown, yes, left--));
                    button.postDelayed(this, 1000);
                } else {
                    button.setText(yes);
                    button.setEnabled(true);
                }
            }
        });
    }

    /** The swiped book is gone from the list; it is deleted unless "undo" is tapped before the bar leaves (UI-10). */
    @Override
    public void showUndoRemove(@NonNull BookPOJO book) {
        if (binding == null) {
            mPresenter.commitRemove();
            return;
        }
        Snackbar.make(binding.getRoot(), R.string.book_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> mPresenter.undoRemove())
                .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            mPresenter.commitRemove();
                        }
                    }
                })
                .show();
    }

    @Override
    public void showFilterValues(int filterId, @NonNull ArrayList<String> values) {
        if (binding == null) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(requireContext(), binding.view, Gravity.END);
        popupMenu.getMenu().add(1, 0, 0, R.string.all);
        for (int i = 0; i < values.size(); i++) {
            popupMenu.getMenu().add(1, i + 1, 0, values.get(i));
        }
        popupMenu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            mPresenter.onFilterSelected(filterId, index == 0 ? null : values.get(index - 1));
            return true;
        });
        popupMenu.show();
    }

    @SuppressLint("InflateParams")
    @Override
    public void showBookMenu(@NonNull BookPOJO book) {
        if (bookMenu != null) {
            bookMenu.dismiss();
        }
        View layout = getLayoutInflater().inflate(R.layout.bootom_sheet_books_menu, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(layout);
        bookMenu = dialog;
        dialog.setOnDismissListener(d -> {
            if (bookMenu == dialog) {
                bookMenu = null;
            }
        });

        TextView open = layout.findViewById(R.id.open);
        TextView add = layout.findViewById(R.id.addFavorite);
        TextView remove = layout.findViewById(R.id.removeFavorite);
        TextView genre = layout.findViewById(R.id.genre);
        TextView author = layout.findViewById(R.id.author);
        TextView artist = layout.findViewById(R.id.artist);
        TextView series = layout.findViewById(R.id.series);

        ImageView imageView = layout.findViewById(R.id.imageView);
        Drawable mask = ParentalControl.coverMask(imageView.getContext(), book);
        if (mask != null) {
            imageView.setImageDrawable(mask);
        } else if (DefaultCover.replaces(book.getPhoto())) {
            imageView.setImageDrawable(DefaultCover.drawable(imageView.getContext(), book.getUrl()));
        } else {
            Picasso.get()
                    .load(book.getPhoto())
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(imageView);
        }

        TextView title = layout.findViewById(R.id.title);
        title.setText(book.getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (book.getAutor() != null) {
            authorName.setText(book.getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        add.setVisibility(View.GONE);
        remove.setVisibility(View.VISIBLE);
        remove.setText(R.string.remove);
        remove.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_minus_circle, 0, 0, 0);

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            showBooksActivity(book);
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            mPresenter.onRemove(book);
        });

        genre.setVisibility(book.getUrlGenre() != null ? View.VISIBLE : View.GONE);
        genre.setOnClickListener(view1 -> {
            dialog.dismiss();
            mPresenter.onMenuGenre(book.getUrlGenre(), book.getGenre());
        });

        author.setVisibility(book.getUrlAutor() != null ? View.VISIBLE : View.GONE);
        author.setOnClickListener(view1 -> {
            dialog.dismiss();
            mPresenter.onMenuAutor(book.getUrlAutor(), book.getAutor());
        });

        artist.setVisibility(book.getUrlArtist() != null ? View.VISIBLE : View.GONE);
        artist.setOnClickListener(view1 -> {
            dialog.dismiss();
            mPresenter.onMenuArtist(book.getUrlArtist(), book.getArtist());
        });

        series.setVisibility(book.getSeries() != null && book.getUrlSeries() != null ? View.VISIBLE : View.GONE);
        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            mPresenter.onMenuSeries(book.getUrlSeries(), book.getSeries());
        });
        dialog.show();
    }

    protected FavoritePresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onResume() {
        super.onResume();
        long parentalVersion = ParentalControl.version(requireContext());
        if (parentalVersion != mParentalVersion && mAddapterBooks != null) {
            mAddapterBooks.refreshItem();
        }
        mParentalVersion = parentalVersion;
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
                case TABLE_SAVED:
                    if (navigationView != null) {
                        navigationView.setCheckedItem(R.id.nav_saved);
                    } else if (mTextViewArrayList != null && mTextViewArrayList.size() > 6) {
                        mTextViewArrayList.get(6).setBackgroundResource(SelectedValue.resourceId);
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
        return new FavoritePresenter(table);
    }

    @Override
    public void showSearchActivity(int modelId) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.putExtra(Consts.ARG_MODEL, modelId);
        searchActivityLauncher.launch(intent);
    }
}
