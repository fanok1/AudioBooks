package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.ClearSavedPOJO;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/** @noinspection ClassEscapesDefinedScope*/
public class ClearSavedAdapter extends RecyclerView.Adapter<ClearSavedAdapter.ViewHolder> {

    private ArrayList<ClearSavedPOJO> mData;

    static class ViewHolder extends RecyclerView.ViewHolder {


        private final CheckBox mCheckBox;

        private final TextView mTitle;

        private final View mView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mView = itemView;
            mTitle = itemView.findViewById(R.id.title);
            mCheckBox = itemView.findViewById(R.id.checkBox);

        }
    }

    private OnChackedChangeInterface mChackedChange;

    public ClearSavedAdapter() {
        mData = new ArrayList<>();
        mSelectedItems = new HashSet<>();
    }

    public void setChackedChange(
            OnChackedChangeInterface chackedChange) {
        mChackedChange = chackedChange;
    }


    @SuppressLint("NotifyDataSetChanged")
    public void setData(@NonNull ArrayList<ClearSavedPOJO> newData) {
        mData = newData;
        notifyDataSetChanged();
    }

    public HashSet<File> getSelectedItems() {
        return mSelectedItems;
    }

    public int getSelectedItemsSize() {
        return mSelectedItems.size();
    }

    public ClearSavedPOJO getData(int index) {
        return mData.get(index);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setSelectedAll() {
        mSelectedItems.clear();
        for (int i = 0; i < mData.size(); i++) {
            mSelectedItems.add(mData.get(i).getFile());
        }
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelected() {
        mSelectedItems.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.clear_saved_recycler_item,
                viewGroup, false);

        return new ViewHolder(view);
    }

    public interface OnChackedChangeInterface {

        void onChackedChange();
    }

    private final HashSet<File> mSelectedItems;

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        final int position = viewHolder.getBindingAdapterPosition();
        viewHolder.mView.setOnClickListener(view -> {
            if (position != RecyclerView.NO_POSITION) {
                if (viewHolder.mCheckBox.isChecked()) {
                    mSelectedItems.remove(mData.get(position).getFile());
                } else {
                    mSelectedItems.add(mData.get(position).getFile());
                }
                if (mChackedChange != null) {
                    mChackedChange.onChackedChange();
                }
                notifyItemChanged(position);
            }
        });

        String text = mData.get(i).getFile().getPath() + " (" + mData.get(i).getStorege() + ")";
        viewHolder.mTitle.setText(text);
        viewHolder.mCheckBox.setChecked(mSelectedItems.contains(mData.get(i).getFile()));
        viewHolder.mCheckBox.setClickable(false);

    }
}
