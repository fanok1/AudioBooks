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
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.util.DownloadUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/** @noinspection ClassEscapesDefinedScope*/
@UnstableApi
public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

    private static final String TAG = "AudioAdapter";

    private int indexSelected = -1;

    private ArrayList<AudioPOJO> mData;

    private AudioAdapter.OnListItemSelectedInterface mListener;

    private AudioAdapter.OnSelectedListner mSelectedListner;
    private final Map<String, Download> allDownloads;

    private final  int[] states = new int[] {
            Download.STATE_COMPLETED,
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_FAILED,
            Download.STATE_STOPPED,
            Download.STATE_REMOVING,
            Download.STATE_RESTARTING
    };



    static class ViewHolder extends RecyclerView.ViewHolder {


        private final ImageView mImageView;

        private final ProgressBar mProgressBar;

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

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() {
        allDownloads.clear();
        try {
            DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
            try (DownloadCursor cursor = index.getDownloads(states)) {
                while (cursor.moveToNext()) {
                    Download d = cursor.getDownload();
                    allDownloads.put(d.request.id, d);
                }
            }

        } catch (IOException e) {
            Log.w(TAG, "Failed to query downloads", e);
        }
        notifyDataSetChanged();
    }

    public HashSet<String> getSelectedItems() {
        return mSelectedItems;
    }

    public int getSelectedItemsSize() {
        return mSelectedItems.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(@NonNull ArrayList<AudioPOJO> data) {
        mData = data;
        notifyDataSetChanged();
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

    private final HashSet<String> mSelectedItems;

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelected() {
        mSelectedItems.clear();
        if (mSelectedListner != null) {
            mSelectedListner.onItemSelected();
        }
        notifyDataSetChanged();
    }


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
        Download download = allDownloads.get(mData.get(i).getCleanUrl());
        if (download != null && java.util.stream.IntStream.of(states).anyMatch(v -> v == download.state)){
            if (download.state == Download.STATE_COMPLETED) {
                viewHolder.mImageView.setVisibility(View.VISIBLE);
                viewHolder.mImageView.setImageResource(R.drawable.ic_is_check);
                viewHolder.mProgressBar.setVisibility(View.GONE);
            } else if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED){
                viewHolder.mImageView.setVisibility(View.INVISIBLE);
                viewHolder.mProgressBar.setVisibility(View.VISIBLE);
            }else if (download.state == Download.STATE_FAILED){
                viewHolder.mProgressBar.setVisibility(View.GONE);
                viewHolder.mImageView.setImageResource(R.drawable.ic_error);
                viewHolder.mImageView.setVisibility(View.VISIBLE);
            }else if (download.state == Download.STATE_STOPPED){
                viewHolder.mProgressBar.setVisibility(View.GONE);
                viewHolder.mImageView.setImageResource(R.drawable.ic_pause_circle_outline);
                viewHolder.mImageView.setVisibility(View.VISIBLE);
            }
        } else {
            viewHolder.mImageView.setVisibility(View.INVISIBLE);
            viewHolder.mProgressBar.setVisibility(View.GONE);
        }


        viewHolder.mView.setOnLongClickListener(view -> {
            selectedItemsAdd(url);
            return true;
        });

        UiModeManager uiModeManager = (UiModeManager) viewHolder.mView.getContext().getSystemService(UI_MODE_SERVICE);
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
                                selectedItemsAdd(url);
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

        if (!mSelectedItems.isEmpty()) {
            viewHolder.mRadioButton.setVisibility(View.VISIBLE);
        } else {
            viewHolder.mRadioButton.setVisibility(View.GONE);
        }

        viewHolder.mRadioButton.setOnClickListener(view -> selectedItemsAdd(mData.get(i).getUrl()));

        viewHolder.mRadioButton.setChecked(mSelectedItems.contains(mData.get(i).getUrl()));
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectedItemsAddAll() {

        if (mSelectedItems.size() == mData.size()) {
            mSelectedItems.clear();
        } else {
            mSelectedItems.clear();
            for (AudioPOJO audioPOJO : mData) {
                mSelectedItems.add(audioPOJO.getUrl());
            }
        }
        if (mSelectedListner != null) {
            mSelectedListner.onItemSelected();
        }

        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void click(View view, int pos) {
        if (mSelectedItems.isEmpty()) {
            indexSelected = pos;
            if (mListener != null) {
                mListener.onItemSelected(view, pos);
            }
            notifyDataSetChanged();
        } else {
            selectedItemsAdd(mData.get(pos).getUrl());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void selectedItemsAdd(String s) {
        if (mSelectedItems.contains(s)) {
            mSelectedItems.remove(s);
        } else {
            mSelectedItems.add(s);
        }
        if (mSelectedListner != null) {
            mSelectedListner.onItemSelected();
        }
        notifyDataSetChanged();
    }
}
