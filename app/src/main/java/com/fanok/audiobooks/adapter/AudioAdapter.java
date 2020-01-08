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
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.AudioPOJO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
    private static final String TAG = "AudioAdapter";
    private int indexSelected = -1;

    private ArrayList<AudioPOJO> mData;
    private AudioAdapter.OnListItemSelectedInterface mListener;
    private AudioAdapter.OnSelectedListner mSelectedListner;
    private HashSet<String> mSelectedItems;

    public AudioAdapter() {
        mData = new ArrayList<>();
        mSelectedItems = new HashSet<>();
    }

    public void setSelectedListner(
            OnSelectedListner selectedListner) {
        mSelectedListner = selectedListner;
    }

    public HashSet<String> getSelectedItems() {
        return mSelectedItems;
    }

    public int getSelectedItemsSize() {
        return mSelectedItems.size();
    }

    public void setData(@NonNull ArrayList<AudioPOJO> data) {
        mData = data;
        notifyDataSetChanged();
    }

    public void setIndexSelected(int indexSelected) {
        this.indexSelected = indexSelected;
        notifyDataSetChanged();
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Log.d(TAG, "onBindViewHolder: called");

        int totalSecs = mData.get(i).getTime();
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;
        String timeString = String.format(Locale.forLanguageTag("UK"), "%02d:%02d:%02d", hours,
                minutes, seconds);

        viewHolder.mTime.setText(timeString);
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



        viewHolder.mView.setOnLongClickListener(view -> {
            selectedItemsAdd(mData.get(i).getUrl());
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

        if (mSelectedItems.contains(mData.get(i).getUrl())) {
            viewHolder.mRadioButton.setChecked(true);
        } else {
            viewHolder.mRadioButton.setChecked(false);
        }
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

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public interface OnListItemSelectedInterface {
        void onItemSelected(View view, int position);
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

    public void clearSelected() {
        mSelectedItems.clear();
        if (mSelectedListner != null) {
            mSelectedListner.onItemSelected();
        }
        notifyDataSetChanged();
    }

    public interface OnSelectedListner {
        void onItemSelected();
    }

    class ViewHolder extends RecyclerView.ViewHolder {


        private View mView;
        private TextView mTitle;
        private TextView mTime;
        private RadioButton mRadioButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mView = itemView;
            mTitle = itemView.findViewById(R.id.title);
            mTime = itemView.findViewById(R.id.time);
            mRadioButton = itemView.findViewById(R.id.radio);
        }
    }
}
