package com.fanok.audiobooks.adapter;

import static android.content.Context.UI_MODE_SERVICE;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.download.DownloadStatus;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.util.DeletionPlanner;
import com.fanok.audiobooks.util.DownloadUtil;
import com.github.lzyzsd.circleprogress.DonutProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** @noinspection ClassEscapesDefinedScope*/
@OptIn(markerClass = UnstableApi.class)
public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

    private static final String TAG = "AudioAdapter";

    private int indexSelected = -1;

    private ArrayList<AudioPOJO> mData;
    private int mChaptersTotal;
    /** Positions of the chapters held as exported copies in the download folder (their download may be gone). */
    private final java.util.Set<Integer> exported = new HashSet<>();

    private AudioAdapter.OnListItemSelectedInterface mListener;

    private AudioAdapter.OnSelectedListner mSelectedListner;
    /** Chapter URL → the download holding it (by its DownloadIds entry, else its legacy id). */
    private final Map<String, Download> allDownloads;
    private String bookUrl;

    /** Change payload: only the chapter's download state is redrawn. */
    private static final Object LIVE = new Object();

    public interface OnStatusListener {
        /** The book's download status, after every index read and every monitor tick. */
        void onDownloadStatus(@NonNull DownloadStatus status);
    }

    private OnStatusListener mStatusListener;
    private final DownloadMonitor.Listener mLive = this::onTick;

    private final int[] states = new int[] {
            Download.STATE_COMPLETED,
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_FAILED,
            Download.STATE_STOPPED,
            Download.STATE_RESTARTING
    };



    static class ViewHolder extends RecyclerView.ViewHolder {


        private final ImageView mImageView;

        private final ProgressBar mProgressBar;

        private final DonutProgress mDonut;

        private final RadioButton mRadioButton;

        private final TextView mTime;

        private final TextView mTitle;

        private final View mView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mView = itemView;
            mTitle = itemView.findViewById(R.id.title);
            mTime = itemView.findViewById(R.id.time);
            mRadioButton = itemView.findViewById(R.id.radio);
            mImageView = itemView.findViewById(R.id.is_download);
            mProgressBar = itemView.findViewById(R.id.progressBar);
            mDonut = itemView.findViewById(R.id.download_progress);
        }
    }

    public interface OnListItemSelectedInterface {

        void onItemSelected(View view, int position);
    }

    public AudioAdapter() {
        mData = new ArrayList<>();
        mSelectedItems = new HashSet<>();
        allDownloads = new HashMap<>();
        refresh();
    }

    public void setSelectedListner(OnSelectedListner selectedListner) {
        mSelectedListner = selectedListner;
    }

    public void setStatusListener(OnStatusListener listener) {
        mStatusListener = listener;
    }

    /**
     * Live chapter rings and status while the screen is visible (its onStart/onStop): the process-wide monitor must
     * not keep a stopped screen. The screen's onResume {@link #refresh}es what changed meanwhile.
     */
    public void startLive() {
        DownloadMonitor.get().addListener(mLive);
    }

    public void stopLive() {
        DownloadMonitor.get().removeListener(mLive);
    }

    /** A monitor tick: redraws the chapters in flight with the manager's live state and republishes the status. */
    private void onTick() {
        for (int i = 0; i < mData.size(); i++) {
            String url = mData.get(i).getUrl();
            Download d = url != null ? allDownloads.get(url) : null;
            if (d != null && d.state != Download.STATE_COMPLETED) notifyItemChanged(i, LIVE);
        }
        publishStatus();
    }

    /**
     * The book's status from the chapter list: held chapters (completed download or local file), chosen ones (any
     * download or local file) and the transfers, one per download id, refreshed by the monitor.
     */
    private void publishStatus() {
        if (mStatusListener == null) return;
        Map<String, DownloadStatus.Part> parts = new HashMap<>();
        int done = 0;
        int selected = 0;
        for (int i = 0; i < mData.size(); i++) {
            String url = mData.get(i).getUrl();
            if (url == null) continue;
            if (!DeletionPlanner.isRemote(url) || exported.contains(i)) {
                done++;
                selected++;
                continue;
            }
            Download d = allDownloads.get(url);
            if (d == null) continue;
            selected++;
            if (d.state == Download.STATE_COMPLETED) done++;
            if (!parts.containsKey(d.request.id)) parts.put(d.request.id, DownloadMonitor.partOf(d));
        }
        mStatusListener.onDownloadStatus(DownloadMonitor.get().statusOf(new ArrayList<>(parts.values()), done,
                selected, Math.max(mChaptersTotal, mData.size())));
    }

    /** The book whose chapters are listed: their downloads are looked up by their ids within it. */
    public void setBookUrl(String bookUrl) {
        this.bookUrl = bookUrl;
        refresh();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() {
        final String book = bookUrl;
        final List<String> urls = new ArrayList<>();
        for (AudioPOJO audio : mData) urls.add(audio.getUrl());
        DownloadUtil.runInBackground(() -> {
            final HashMap<String, Download> byUrl = new HashMap<>();
            try {
                DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
                for (Map.Entry<String, Download> e : DownloadUtil.downloadsByChapterUrl(
                        App.getInstance(), index, book, urls).entrySet()) {
                    if (isStateMatch(states, e.getValue().state)) byUrl.put(e.getKey(), e.getValue());
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query downloads", e);
            }
            java.util.Set<Integer> copies = new HashSet<>();
            try {
                copies = DownloadUtil.exportedChapters(App.getInstance(), book, urls);
            } catch (Exception e) {
                Log.w(TAG, "Failed to read exported chapters", e);
            }
            final java.util.Set<Integer> exportedNow = copies;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                exported.clear();
                exported.addAll(exportedNow);
                allDownloads.clear();
                allDownloads.putAll(byUrl);
                notifyDataSetChanged();
                publishStatus();
            });
        });
    }

    /** A copy of the chosen chapter URLs, so clearing the selection never changes what was handed over. */
    public HashSet<String> getSelectedItems() {
        return new HashSet<>(mSelectedItems);
    }

    /** The chosen files: what download and delete act on. */
    public int getSelectedItemsSize() {
        return mSelectedItems.size();
    }

    /** True while chapters are being chosen for download/delete, even when none is chosen at the moment. */
    public boolean isSelecting() {
        return mSelecting;
    }

    /** The chapters whose file is chosen (several chapters of one file count each). */
    public int getSelectedChapters() {
        int count = 0;
        for (AudioPOJO audio : mData) {
            if (audio.getUrl() != null && mSelectedItems.contains(audio.getUrl())) count++;
        }
        return count;
    }

    public boolean isAllSelected() {
        return !mData.isEmpty() && getSelectedChapters() == mData.size();
    }

    /** Starts choosing with every chapter chosen (the Download/Delete menu); while choosing, chooses them all. */
    public void startSelection() {
        mSelecting = true;
        setAllSelected(true);
    }

    /** Chooses every chapter, or none; the selection goes on either way. */
    public void setAllSelected(boolean all) {
        mSelectedItems.clear();
        if (all) {
            for (AudioPOJO audio : mData) {
                if (audio.getUrl() != null) mSelectedItems.add(audio.getUrl());
            }
        }
        selectionChanged();
    }

    /** Ends choosing: the check marks go and a tap plays a chapter again. */
    public void endSelection() {
        if (!mSelecting) return;
        mSelecting = false;
        mSelectedItems.clear();
        selectionChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void selectionChanged() {
        if (mSelectedListner != null) {
            mSelectedListner.onItemSelected();
        }
        notifyDataSetChanged();
    }

    /**
     * The chapters to list, of {@code chaptersTotal} the book has: more than listed when only the downloaded ones are
     * (offline), so a partly downloaded book never reads as complete.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setData(@NonNull ArrayList<AudioPOJO> data, int chaptersTotal) {
        mData = data;
        mChaptersTotal = chaptersTotal;
        if (mSelecting) {
            // a reload keeps the choice of the files still listed
            HashSet<String> listed = new HashSet<>();
            for (AudioPOJO audio : data) listed.add(audio.getUrl());
            mSelectedItems.retainAll(listed);
            if (mSelectedListner != null) mSelectedListner.onItemSelected();
        }
        notifyDataSetChanged();
        refresh();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setIndexSelected(int indexSelected) {
        this.indexSelected = indexSelected;
        notifyDataSetChanged();
    }

    public int getIndexSelected() {
        return indexSelected;
    }

    public AudioPOJO getData(int index) {
        return mData.get(index);
    }

    public void setListener(OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        Log.d(TAG, "onCreateViewHolder: called");

        View view;

        view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.audio_recycler_item,
                viewGroup, false);

        return new ViewHolder(view);
    }

    public interface OnSelectedListner {

        void onItemSelected();
    }

    /** The chosen chapters, by URL: a file is downloaded or deleted as a whole, whichever chapters it holds. */
    private final HashSet<String> mSelectedItems;
    private boolean mSelecting;
    /** The hint that chapters of one file are chosen together, shown once per screen. */
    private boolean mSharedFileHintShown;


    @Override
    public int getItemCount() {
        return mData.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, @SuppressLint("RecyclerView") int i) {
        Log.d(TAG, "onBindViewHolder: called");

        if (mData.get(i).getTime() != 0) {
            int totalSecs = mData.get(i).getTime();
            int hours = totalSecs / 3600;
            int minutes = (totalSecs % 3600) / 60;
            int seconds = totalSecs % 60;
            String timeString = String.format(Locale.forLanguageTag("UK"), "%02d:%02d:%02d", hours,
                    minutes, seconds);

            viewHolder.mTime.setText(timeString);
            viewHolder.mTime.setVisibility(View.VISIBLE);
        } else {
            viewHolder.mTime.setVisibility(View.GONE);
        }

        viewHolder.mTitle.setText(mData.get(i).getName());

        viewHolder.mView.setOnClickListener(view -> click(view, i));


        viewHolder.mView.setOnKeyListener((view, i1, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getKeyCode() == KEYCODE_DPAD_CENTER || event.getKeyCode() == KEYCODE_ENTER
                        || event.getKeyCode() == KEYCODE_NUMPAD_ENTER) {
                    click(viewHolder.mView, i);
                    return true;
                }
            }
            return false;
        });


        String url = mData.get(i).getUrl();
        bindDownloadState(viewHolder, i);


        viewHolder.mView.setOnLongClickListener(view -> {
            longClick(view, i);
            return true;
        });

        UiModeManager uiModeManager = (UiModeManager) viewHolder.mView.getContext().getApplicationContext().getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            // --- Логика для ТВ ---
            // Устанавливаем OnKeyListener для эмуляции долгого нажатия
            viewHolder.mView.setOnKeyListener(new View.OnKeyListener() {
                private long keyHeldDownTime = 0;
                private final long LONG_PRESS_DURATION_MS = 500; // 0.5 секунды

                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    // Нас интересуют только центральные кнопки пульта
                    if (keyCode == KEYCODE_DPAD_CENTER || keyCode == KEYCODE_ENTER || keyCode == KEYCODE_NUMPAD_ENTER) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            // Если это первое нажатие, запоминаем время
                            if (keyHeldDownTime == 0) {
                                keyHeldDownTime = event.getEventTime();
                            }
                            return true; // Мы обработаем событие, когда кнопка будет отпущена
                        }

                        if (event.getAction() == KeyEvent.ACTION_UP) {
                            // Кнопку отпустили
                            long duration = event.getEventTime() - keyHeldDownTime;
                            keyHeldDownTime = 0; // Сбрасываем таймер

                            if (duration >= LONG_PRESS_DURATION_MS) {
                                // Если удержание было достаточно долгим, считаем это "долгим кликом"
                                longClick(v, i);
                                return true; // Событие полностью обработано
                            } else {
                                // Если удержание было коротким, считаем это обычным кликом
                                click(v, i);
                                return true; // Событие полностью обработано
                            }
                        }
                    }
                    return false; // Для всех остальных кнопок возвращаем false
                }
            });

        }


        if (indexSelected != i) {
            TypedValue outValue = new TypedValue();
            viewHolder.mView.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true);
            viewHolder.mView.setBackgroundResource(outValue.resourceId);
        } else {
            viewHolder.mView.setBackgroundColor(
                    Consts.getAttributeColor(viewHolder.mView.getContext(), R.attr.backgroundItem));
        }

        viewHolder.mRadioButton.setVisibility(mSelecting ? View.VISIBLE : View.GONE);
        viewHolder.mRadioButton.setChecked(url != null && mSelectedItems.contains(url));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i, @NonNull List<Object> payloads) {
        if (payloads.contains(LIVE)) {
            bindDownloadState(viewHolder, i);
        } else {
            super.onBindViewHolder(viewHolder, i, payloads);
        }
    }

    /**
     * The chapter's download as an icon: held, a ring of how much is here (the manager's live bytes), queued, failed
     * or paused. The spinner stays only while the download does not know its size yet.
     */
    private void bindDownloadState(@NonNull ViewHolder viewHolder, int position) {
        String url = mData.get(position).getUrl();
        Download download = url != null ? allDownloads.get(url) : null;
        if (download != null) download = DownloadMonitor.get().refresh(download);
        viewHolder.mImageView.setVisibility(View.INVISIBLE);
        viewHolder.mProgressBar.setVisibility(View.GONE);
        if (viewHolder.mDonut != null) viewHolder.mDonut.setVisibility(View.GONE);
        if (exported.contains(position)) {
            viewHolder.mImageView.setImageResource(R.drawable.ic_is_check);
            viewHolder.mImageView.setContentDescription(null);
            viewHolder.mImageView.setVisibility(View.VISIBLE);
            return;
        }
        if (download == null || !isStateMatch(states, download.state)) return;
        switch (download.state) {
            case Download.STATE_COMPLETED:
                boolean isActuallyPresent = true;
                if (url.startsWith("file://") || url.startsWith("/")) {
                    String p = url.replace("file://", "");
                    if (!new java.io.File(p).exists()) isActuallyPresent = false;
                }
                if (isActuallyPresent) {
                    viewHolder.mImageView.setImageResource(R.drawable.ic_is_check);
                    viewHolder.mImageView.setContentDescription(null);
                    viewHolder.mImageView.setVisibility(View.VISIBLE);
                }
                break;
            case Download.STATE_DOWNLOADING:
            case Download.STATE_RESTARTING:
                int percent = DownloadMonitor.percentOf(download);
                if (percent < 0 || viewHolder.mDonut == null) {
                    viewHolder.mProgressBar.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.mDonut.setProgress(percent);
                    viewHolder.mDonut.setVisibility(View.VISIBLE);
                }
                break;
            case Download.STATE_QUEUED:
                viewHolder.mImageView.setImageResource(R.drawable.ic_progress_download);
                viewHolder.mImageView.setContentDescription(
                        viewHolder.mView.getContext().getString(R.string.download_chapter_queued));
                viewHolder.mImageView.setVisibility(View.VISIBLE);
                break;
            case Download.STATE_FAILED:
                viewHolder.mImageView.setImageResource(R.drawable.ic_error);
                viewHolder.mImageView.setContentDescription(
                        viewHolder.mView.getContext().getString(R.string.download_status_failed));
                viewHolder.mImageView.setVisibility(View.VISIBLE);
                break;
            case Download.STATE_STOPPED:
                viewHolder.mImageView.setImageResource(R.drawable.ic_pause_circle_outline);
                viewHolder.mImageView.setContentDescription(
                        viewHolder.mView.getContext().getString(R.string.download_status_paused));
                viewHolder.mImageView.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    /** A tap plays the chapter; while choosing it chooses the chapter or takes it back. */
    @SuppressLint("NotifyDataSetChanged")
    private void click(View view, int pos) {
        if (mSelecting) {
            toggle(view, pos);
            return;
        }
        indexSelected = pos;
        if (mListener != null) {
            mListener.onItemSelected(view, pos);
        }
        notifyDataSetChanged();
    }

    /** A long press starts choosing with this chapter alone; while choosing it is a tap. */
    private void longClick(View view, int pos) {
        if (!mSelecting) {
            mSelecting = true;
            mSelectedItems.clear();
        }
        toggle(view, pos);
    }

    /** Chooses the chapter's file or takes it back: every chapter the file holds goes with it. */
    private void toggle(View view, int pos) {
        String url = pos < mData.size() ? mData.get(pos).getUrl() : null;
        if (url != null) {
            if (!mSelectedItems.remove(url)) {
                mSelectedItems.add(url);
            }
            if (!mSharedFileHintShown && sharesFile(pos)) {
                mSharedFileHintShown = true;
                Toast.makeText(view.getContext(), R.string.selection_shared_file, Toast.LENGTH_LONG).show();
            }
        }
        selectionChanged();
    }

    /** Whether another chapter is in the same file (clips of one stream, as akniga lists them). */
    private boolean sharesFile(int pos) {
        String url = mData.get(pos).getUrl();
        for (int i = 0; i < mData.size(); i++) {
            if (i != pos && url.equals(mData.get(i).getUrl())) return true;
        }
        return false;
    }

    private boolean isStateMatch(int[] states, int dlState) {
        if (states == null) return false;
        for (int s : states) {
            if (s == dlState) return true;
        }
        return false;
    }
}
