package com.fanok.audiobooks.adapter;

import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.AudioPOJO;

import java.util.ArrayList;
import java.util.Locale;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.ViewHolder> {
    private static final String TAG = "AudioAdapter";
    private int indexSelected = -1;

    private ArrayList<AudioPOJO> mData;
    private AudioAdapter.OnListItemSelectedInterface mListener;

    public AudioAdapter() {
        mData = new ArrayList<>();
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
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.audio_recycler_item,
                viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Log.d(TAG, "onBindViewHolder: called");

        int totalSecs = mData.get(i).getTime();
        int hours = totalSecs / 3600;
        int minutes = (totalSecs % 3600) / 60;
        int seconds = totalSecs % 60;
        String timeString = String.format(Locale.forLanguageTag("UA"), "%02d:%02d:%02d", hours,
                minutes, seconds);

        viewHolder.mTime.setText(timeString);
        viewHolder.mTitle.setText(mData.get(i).getName());

        viewHolder.mView.setOnClickListener(view -> {
            indexSelected = i;
            if (mListener != null) {
                mListener.onItemSelected(view, i);
            }
            notifyDataSetChanged();
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
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public interface OnListItemSelectedInterface {
        void onItemSelected(View view, int position);
    }

    class ViewHolder extends RecyclerView.ViewHolder {


        private View mView;
        private TextView mTitle;
        private TextView mTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mView = itemView;
            mTitle = itemView.findViewById(R.id.title);
            mTime = itemView.findViewById(R.id.time);
        }
    }
}
