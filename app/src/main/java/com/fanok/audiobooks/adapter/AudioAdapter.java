package com.fanok.audiobooks.adapter;

import static android.content.Context.UI_MODE_SERVICE;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;

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
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {

    private static final String TAG = "AudioAdapter";

    private int indexSelected = -1;

    private ArrayList<AudioPOJO> mData;

    private String mUrlBook;

    private AudioAdapter.OnListItemSelectedInterface mListener;

    private AudioAdapter.OnSelectedListner mSelectedListner;

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
        mDownloadingItems = new HashSet<>();
    }

    public void setSelectedListner(
            OnSelectedListner selectedListner) {
        mSelectedListner = selectedListner;
    }

    public void addDownloadingItem(@NonNull String url) {
        mDownloadingItems.add(url);
        notifyDataSetChanged();
    }

    public void removeDownloadingItem(@NonNull String url) {
        mDownloadingItems.remove(url);
        notifyDataSetChanged();
    }

    public void clearDownloadingItem() {
        mDownloadingItems.clear();
        notifyDataSetChanged();
    }


    public HashSet<String> getSelectedItems() {
        return mSelectedItems;
    }

    public int getSelectedItemsSize() {
        return mSelectedItems.size();
    }

    public void setData(@NonNull ArrayList<AudioPOJO> data, @NonNull String urlBook) {
        mData = data;
        mUrlBook = urlBook;
        notifyDataSetChanged();
    }

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
        UiModeManager uiModeManager = (UiModeManager) viewGroup.getContext().getSystemService(
                UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.audio_recycler_item_television,
                    viewGroup, false);
        } else {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.audio_recycler_item,
                    viewGroup, false);
        }

        return new ViewHolder(view);
    }

    public interface OnSelectedListner {

        void onItemSelected();
    }

    private final HashSet<String> mDownloadingItems;

    private final HashSet<String> mSelectedItems;

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
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
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

        File[] folders = viewHolder.mImageView.getContext().getExternalFilesDirs(null);
        boolean b = false;
        BooksDBModel dbModel = new BooksDBModel(viewHolder.mImageView.getContext());
        if(dbModel.inSaved(mUrlBook)) {
            BookPOJO bookPOJO = dbModel.getSaved(mUrlBook);
            String source = Consts.getSorceName(viewHolder.mImageView.getContext(), mUrlBook);
            for (File folder : folders) {
                if (folder != null) {
                    String filePath = folder.getAbsolutePath() + "/" + source
                            + "/" + bookPOJO.getAutor()
                            + "/" + bookPOJO.getArtist()
                            + "/" + bookPOJO.getName();
                    File dir = new File(filePath);
                    if (dir.exists() && dir.isDirectory()) {
                        File file;
                        if(!Objects.equals(source, viewHolder.mImageView.getContext().getString(R.string.abook))) {
                            String url = mData.get(i).getUrl();
                            file = new File(dir, url.substring(url.lastIndexOf("/") + 1));
                        }else {
                            file = new File(dir+"/pl","enc.key");
                        }

                        if (file.exists()) {
                            b = true;
                            break;
                        }
                    }
                }
            }
        }
        dbModel.closeDB();
        if (b) {
            viewHolder.mImageView.setVisibility(View.VISIBLE);
            viewHolder.mProgressBar.setVisibility(View.GONE);
        } else {
            viewHolder.mImageView.setVisibility(View.INVISIBLE);
            if (mDownloadingItems.contains(mData.get(i).getUrl())) {
                viewHolder.mProgressBar.setVisibility(View.VISIBLE);
            } else {
                viewHolder.mProgressBar.setVisibility(View.GONE);
            }
        }



        viewHolder.mView.setOnLongClickListener(view -> {
            String url = mData.get(i).getUrl();
            if(url.contains(Url.SERVER_AKNIGA)){
                selectedItemsAddAll();
            }else {
                selectedItemsAdd(url);
            }
            return true;
        });


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
