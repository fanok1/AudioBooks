package com.fanok.audiobooks.fragment;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.SourceHealth;
import com.fanok.audiobooks.search.SourceProbe;
import com.fanok.audiobooks.search.Sources;
import com.google.android.material.color.MaterialColors;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "Источники" setting: every site in the user's order, the active ones above a divider row that shows and steps
 * their count; each row carries the site's last availability result and its route (direct or through the proxy).
 * Every change applies at once. The availability is checked only on request (the refresh button), for a site whose
 * route changed, and for a site that becomes active without a kept result: never on opening, it costs traffic.
 */
public class SourcesDialogFragment extends DialogFragment {

    public static final String TAG = "SourcesDialogFragment";
    /** Sent to the parent's fragment manager when the dialog closes. */
    public static final String RESULT_CLOSED = "sources_dialog_closed";

    private static final int TYPE_SOURCE = 0;
    private static final int TYPE_DIVIDER = 1;
    /** Fixed, not the theme's: they must read on the light, the dark and the black background alike. */
    static final int COLOR_UP = 0xFF4CAF50;
    static final int COLOR_DOWN = 0xFFE53935;
    /** The status of a site never checked, or of an inactive one. */
    static final String UNKNOWN = "—";
    private static final int MENU_UP = 1;
    private static final int MENU_DOWN = 2;
    private static final int MENU_TOGGLE = 3;
    /** Sites checked at once by the refresh button. */
    private static final int PARALLEL = 4;
    /** A rebind payload: the row changes in place, without the item animator's cross-fade. */
    private static final Object REBIND = new Object();

    /** The height of a source row (item_source.xml): the list shows twelve of them. */
    private static final int ROW_DP = 36;
    private SharedPreferences prefs;
    private final List<Object> rows = new ArrayList<>();
    private final Map<Provider, SourceProbe.Result> results = new EnumMap<>(Provider.class);
    private final Set<Provider> checking = EnumSet.noneOf(Provider.class);
    /** Grows at each check of a site, so a result of an earlier check (before a later change of route) is dropped. */
    private final Map<Provider, Integer> generation = new EnumMap<>(Provider.class);
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Adapter adapter = new Adapter();
    private ItemTouchHelper touchHelper;
    private TextView checkedAt;
    private TextView hint;
    private View refresh;
    private View refreshProgress;
    /** Checks under way; the refresh button is a spinner while there is one. */
    private int running;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // rebuilt on every (re)creation: the fragment outlives its dialog across a rotation
        rows.clear();
        rows.addAll(Rows.of(Sources.all(), Sources.activeCount()));
        results.clear();
        results.putAll(SourceHealth.last(prefs));
        checking.clear();
        running = 0;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_sources, null, false);
        checkedAt = view.findViewById(R.id.sourcesCheckedAt);
        hint = view.findViewById(R.id.sourcesProxyHint);
        refresh = view.findViewById(R.id.sourcesRefresh);
        refreshProgress = view.findViewById(R.id.sourcesRefreshProgress);
        view.findViewById(R.id.sourcesAllDirect).setOnClickListener(v -> setAllDirect(true));
        view.findViewById(R.id.sourcesAllProxy).setOnClickListener(v -> setAllDirect(false));
        refresh.setOnClickListener(v -> check(Sources.active(), PARALLEL));

        RecyclerView list = view.findViewById(R.id.sourcesList);
        // a fixed height that fits most phones: twelve rows, less on a short (landscape) screen
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        list.getLayoutParams().height = (int) Math.min(12 * ROW_DP * metrics.density, metrics.heightPixels * 0.6f);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new DragCallback());
        touchHelper.attachToRecyclerView(list);

        hint.setText(hintText(context));
        hint.setMovementMethod(LinkMovementMethod.getInstance());
        getChildFragmentManager().setFragmentResultListener(ProxyDialogFragment.RESULT_CLOSED, this,
                (key, result) -> renderHint());
        renderHint();
        renderCheckedAt();

        return builder.setTitle(R.string.sources_title)
                .setView(view)
                .setPositiveButton(R.string.proxy_close, null)
                .create();
    }

    @Override
    public void onDestroyView() {
        disposables.clear(); // not dispose(): the same fragment builds a new dialog after a rotation
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        disposables.clear();
        if (isAdded()) {
            getParentFragmentManager().setFragmentResult(RESULT_CLOSED, new Bundle());
        }
    }

    /** "Прокси выключен … Настроить": the last word opens the proxy dialog over this one. */
    @NonNull
    private CharSequence hintText(@NonNull Context context) {
        SpannableStringBuilder text = new SpannableStringBuilder(context.getString(R.string.sources_proxy_off))
                .append(' ');
        int start = text.length();
        text.append(context.getString(R.string.sources_proxy_setup));
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (getChildFragmentManager().findFragmentByTag(ProxyDialogFragment.TAG) == null) {
                    new ProxyDialogFragment().show(getChildFragmentManager(), ProxyDialogFragment.TAG);
                }
            }
        }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void renderHint() {
        hint.setVisibility(AppProxy.MODE_OFF.equals(AppProxy.mode()) ? View.VISIBLE : View.GONE);
    }

    private void renderCheckedAt() {
        long at = SourceHealth.lastCheckedAt(prefs);
        Context context = checkedAt.getContext();
        checkedAt.setText(at == 0 ? context.getString(R.string.sources_never_checked)
                : context.getString(R.string.sources_checked_at, DateUtils.formatDateTime(context, at,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH)));
    }

    private void renderRefresh() {
        refresh.setVisibility(running > 0 ? View.INVISIBLE : View.VISIBLE);
        refreshProgress.setVisibility(running > 0 ? View.VISIBLE : View.GONE);
    }

    /** Every site direct, or every site through the proxy; the active sites whose route changed are checked again. */
    private void setAllDirect(boolean direct) {
        List<Provider> changed = new ArrayList<>();
        for (Provider provider : Rows.active(rows)) {
            if (AppProxy.isDirect(provider) != direct) {
                changed.add(provider);
            }
        }
        AppProxy.setAllDirect(prefs, direct);
        adapter.notifyItemRangeChanged(0, rows.size(), REBIND);
        check(changed, PARALLEL);
    }

    /** The route button of a row: the other route, then a new check of the site when it is active. */
    private void toggleRoute(@NonNull SourceHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        Provider provider = (Provider) rows.get(position);
        AppProxy.setDirect(prefs, provider, !AppProxy.isDirect(provider));
        adapter.notifyItemChanged(position, REBIND);
        if (position < Rows.divider(rows)) {
            check(Collections.singletonList(provider), 1);
        }
    }

    /** Checks {@code providers} now; each row shows the check until its result comes. */
    private void check(@NonNull List<Provider> providers, int parallel) {
        if (providers.isEmpty()) {
            return;
        }
        Map<Provider, Integer> expected = new EnumMap<>(Provider.class);
        for (Provider provider : providers) {
            Integer before = generation.get(provider);
            int next = before == null ? 1 : before + 1;
            generation.put(provider, next);
            expected.put(provider, next);
            checking.add(provider);
        }
        adapter.notifyItemRangeChanged(0, rows.size(), REBIND);
        running++;
        renderRefresh();
        disposables.add(SourceHealth.check(prefs, providers, parallel)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> onResult(result, expected), e -> onChecked(expected), () -> onChecked(expected)));
    }

    private void onResult(@NonNull SourceProbe.Result result, @NonNull Map<Provider, Integer> expected) {
        Provider provider = result.provider;
        if (!expected.get(provider).equals(generation.get(provider))) {
            return; // a later check of this site is on its way
        }
        results.put(provider, result);
        checking.remove(provider);
        int position = rows.indexOf(provider);
        if (position >= 0) {
            adapter.notifyItemChanged(position, REBIND);
        }
    }

    /** The end of a check: a site it did not reach (an error) is unknown again; the header shows the time. */
    private void onChecked(@NonNull Map<Provider, Integer> expected) {
        running--;
        for (Map.Entry<Provider, Integer> entry : expected.entrySet()) {
            if (entry.getValue().equals(generation.get(entry.getKey()))) {
                checking.remove(entry.getKey());
            }
        }
        renderRefresh();
        renderCheckedAt();
        adapter.notifyItemRangeChanged(0, rows.size(), REBIND);
    }

    /** Moves a row and refreshes the rows the move touched: the moved one, the divider when crossed, the count. */
    private void move(int from, int to) {
        Rows.move(rows, from, to);
        adapter.notifyItemMoved(from, to);
        adapter.notifyItemRangeChanged(Math.min(from, to), Math.abs(from - to) + 1, REBIND);
    }

    /**
     * After a completed change (a drop, the stepper, the menu): stores the order and the count when they changed,
     * and checks a site that was not active before ({@code activeBefore}) and has no kept result yet.
     */
    private void save(@NonNull List<Provider> activeBefore) {
        List<Provider> active = Rows.active(rows);
        if (!Rows.providers(rows).equals(Sources.all()) || active.size() != Sources.activeCount()) {
            Sources.save(prefs, Rows.providers(rows), active.size());
        }
        List<Provider> missing = new ArrayList<>();
        for (Provider provider : active) {
            if (!activeBefore.contains(provider) && !results.containsKey(provider) && !checking.contains(provider)) {
                missing.add(provider);
            }
        }
        check(missing, 1);
    }

    /** The stepper: the source next to the divider crosses it. */
    private void step(int delta) {
        int divider = Rows.divider(rows);
        if (!Rows.canMove(rows, divider + delta, divider)) {
            return;
        }
        List<Provider> before = Rows.active(rows);
        move(divider + delta, divider);
        save(before);
    }

    /** The row menu (the D-pad and TalkBack path): a step up or down, and across the divider. */
    private void showMenu(@NonNull SourceHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        int divider = Rows.divider(rows);
        PopupMenu menu = new PopupMenu(holder.itemView.getContext(), holder.itemView);
        if (position != 0 && position != divider + 1) {
            menu.getMenu().add(0, MENU_UP, 0, R.string.sources_move_up);
        }
        if (position != divider - 1 && position != rows.size() - 1) {
            menu.getMenu().add(0, MENU_DOWN, 0, R.string.sources_move_down);
        }
        menu.getMenu().add(0, MENU_TOGGLE, 0,
                position < divider ? R.string.sources_deactivate : R.string.sources_activate);
        menu.setOnMenuItemClickListener(item -> {
            act(holder.getBindingAdapterPosition(), item.getItemId());
            return true;
        });
        menu.show();
    }

    private void act(int position, int action) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        int divider = Rows.divider(rows);
        int to = action == MENU_UP ? position - 1 : action == MENU_DOWN ? position + 1 : divider;
        if (!Rows.canMove(rows, position, to)) {
            Context context = requireContext();
            Toast.makeText(context, position < divider ? context.getString(R.string.sources_min)
                    : context.getString(R.string.sources_max, Sources.MAX_ACTIVE), Toast.LENGTH_SHORT).show();
            return;
        }
        List<Provider> before = Rows.active(rows);
        move(position, to);
        save(before);
    }

    /** Pure: the status column: the time of a site that answers, the kind of failure, "—" when unknown. */
    @NonNull
    static String statusText(@NonNull Context context, @Nullable SourceProbe.Result result) {
        if (result == null) {
            return UNKNOWN;
        }
        switch (result.status) {
            case OK: // an older version kept no time
                return result.millis > 0 ? context.getString(R.string.sources_seconds, result.millis / 1000f) : UNKNOWN;
            case EMPTY:
                return context.getString(R.string.sources_status_empty);
            case BLOCKED:
                return context.getString(R.string.sources_status_blocked);
            case TIMEOUT:
                return context.getString(R.string.sources_status_timeout);
            default:
                return context.getString(R.string.sources_status_error);
        }
    }

    /** Pure: the color of a result; 0 (none) when unknown. */
    static int statusColor(@Nullable SourceProbe.Result result) {
        return result == null ? 0 : result.isUp() ? COLOR_UP : COLOR_DOWN;
    }

    private static void enable(@NonNull View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1 : 0.38f);
    }

    /**
     * Pure list logic of the dialog's rows: the sources in the user's order with {@link #DIVIDER} after the active
     * ones, so the active count is the divider's index and a move across the divider changes that count by one.
     */
    static final class Rows {
        /** The row between the active and the inactive sources. */
        static final Object DIVIDER = new Object();

        private Rows() {
        }

        @NonNull
        static List<Object> of(@NonNull List<Provider> order, int active) {
            List<Object> rows = new ArrayList<Object>(order);
            rows.add(active, DIVIDER);
            return rows;
        }

        /** The divider's index: the number of active sources. */
        static int divider(@NonNull List<Object> rows) {
            return rows.indexOf(DIVIDER);
        }

        /** The most sources of {@code total} that can be active. */
        static int limit(int total) {
            return Math.min(Sources.MAX_ACTIVE, total);
        }

        /**
         * True when the source at {@code from} may move to {@code to}: always on its own side of the divider; onto or
         * across it only while 1..{@link #limit} sources stay active. The divider itself never moves by itself.
         */
        static boolean canMove(@NonNull List<Object> rows, int from, int to) {
            int divider = divider(rows);
            if (from < 0 || to < 0 || from >= rows.size() || to >= rows.size() || from == divider) {
                return false;
            }
            boolean across = from < divider ? to >= divider : to <= divider;
            int active = from < divider ? divider - 1 : divider + 1;
            return !across || (active >= 1 && active <= limit(rows.size() - 1));
        }

        /** Moves the row at {@code from} to {@code to}; the rows between shift by one, as notifyItemMoved shows it. */
        static void move(@NonNull List<Object> rows, int from, int to) {
            int step = from < to ? 1 : -1;
            for (int i = from; i != to; i += step) {
                Collections.swap(rows, i, i + step);
            }
        }

        /** Every source in the rows' order. */
        @NonNull
        static List<Provider> providers(@NonNull List<Object> rows) {
            List<Provider> providers = new ArrayList<>();
            for (Object row : rows) {
                if (row != DIVIDER) {
                    providers.add((Provider) row);
                }
            }
            return providers;
        }

        /** The sources above the divider. */
        @NonNull
        static List<Provider> active(@NonNull List<Object> rows) {
            return providers(rows.subList(0, divider(rows)));
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) == Rows.DIVIDER ? TYPE_DIVIDER : TYPE_SOURCE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return viewType == TYPE_DIVIDER
                    ? new DividerHolder(inflater.inflate(R.layout.item_sources_divider, parent, false))
                    : new SourceHolder(inflater.inflate(R.layout.item_source, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                @NonNull List<Object> payloads) {
            onBindViewHolder(holder, position); // a payload only skips the animation
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof DividerHolder) {
                bindDivider((DividerHolder) holder);
            } else {
                bindSource((SourceHolder) holder, (Provider) rows.get(position), position < Rows.divider(rows));
            }
        }

        private void bindDivider(@NonNull DividerHolder holder) {
            int active = Rows.divider(rows);
            holder.label.setText(holder.label.getContext().getString(R.string.sources_active, active,
                    Sources.MAX_ACTIVE));
            enable(holder.less, active > 1);
            enable(holder.more, active < Rows.limit(rows.size() - 1));
            holder.less.setOnClickListener(v -> step(-1));
            holder.more.setOnClickListener(v -> step(1));
        }

        @SuppressLint("ClickableViewAccessibility") // the handle is touch only; the row's menu is the accessible path
        private void bindSource(@NonNull SourceHolder holder, @NonNull Provider provider, boolean active) {
            Context context = holder.itemView.getContext();
            SourceProbe.Result result = active ? results.get(provider) : null;
            boolean busy = checking.contains(provider);
            int color = busy ? 0 : statusColor(result);
            holder.title.setText(provider.title);
            holder.status.setText(statusText(context, result));
            holder.status.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
            holder.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            holder.stripe.setBackgroundColor(color != 0 ? color
                    : active ? ColorUtils.setAlphaComponent(holder.secondary, 102) : 0);
            if (color != 0) {
                holder.fade.setColors(new int[]{ColorUtils.setAlphaComponent(color, 46), 0});
                holder.itemView.setBackground(holder.fade);
            } else {
                holder.itemView.setBackground(null);
            }
            float alpha = active ? 1 : 0.45f;
            holder.handle.setAlpha(alpha);
            holder.title.setAlpha(alpha);
            holder.status.setAlpha(alpha);
            boolean direct = AppProxy.isDirect(provider);
            holder.mode.setImageResource(direct ? R.drawable.ic_arrow_right : R.drawable.ic_shield_check);
            ImageViewCompat.setImageTintList(holder.mode,
                    ColorStateList.valueOf(direct ? holder.secondary : holder.accent));
            holder.mode.setContentDescription(context.getString(
                    direct ? R.string.sources_mode_direct : R.string.sources_mode_proxy, provider.title));
            holder.mode.setOnClickListener(v -> toggleRoute(holder));
            holder.itemView.setOnClickListener(v -> showMenu(holder));
            holder.handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }
    }

    static final class SourceHolder extends RecyclerView.ViewHolder {
        final View stripe;
        final ImageView handle;
        final TextView title;
        final TextView status;
        final View progress;
        final ImageButton mode;
        /** The row's background: the status color fading out to the right. */
        final GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0, 0});
        final int secondary;
        final int accent;

        SourceHolder(@NonNull View itemView) {
            super(itemView);
            stripe = itemView.findViewById(R.id.sourceStripe);
            handle = itemView.findViewById(R.id.sourceHandle);
            title = itemView.findViewById(R.id.sourceTitle);
            status = itemView.findViewById(R.id.sourceStatus);
            progress = itemView.findViewById(R.id.sourceProgress);
            mode = itemView.findViewById(R.id.sourceMode);
            secondary = MaterialColors.getColor(itemView, android.R.attr.textColorSecondary);
            accent = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorAccent);
        }
    }

    static final class DividerHolder extends RecyclerView.ViewHolder {
        final TextView label;
        final ImageButton less;
        final ImageButton more;

        DividerHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.sourcesDividerLabel);
            less = itemView.findViewById(R.id.sourcesLess);
            more = itemView.findViewById(R.id.sourcesMore);
        }
    }

    /** Drag by the handle only; the divider stays where the sources leave it; one save at the drop. */
    private final class DragCallback extends ItemTouchHelper.Callback {
        private List<Provider> activeBefore = Collections.emptyList();

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
            return holder instanceof DividerHolder ? 0 : makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current,
                @NonNull RecyclerView.ViewHolder target) {
            return Rows.canMove(rows, current.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder,
                @NonNull RecyclerView.ViewHolder target) {
            int from = holder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (!Rows.canMove(rows, from, to)) {
                return false;
            }
            move(from, to);
            return true;
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder holder, int actionState) {
            super.onSelectedChanged(holder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                activeBefore = Rows.active(rows);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            // the drop: one write for the whole drag. A release during a layout pass (the dragged view detached)
            // cannot notify the adapter, so it waits for the pass to end.
            List<Provider> before = activeBefore;
            if (recyclerView.isComputingLayout()) {
                recyclerView.post(() -> save(before));
            } else {
                save(before);
            }
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
        }
    }
}
