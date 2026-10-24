package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import com.fanok.audiobooks.policy.ParentalControl;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class ParentalControlAddapter extends
        RecyclerView.Adapter<ParentalControlAddapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final CheckedTextView mCheckedTextView;

        private final TextView mTextView;

        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mCheckedTextView = itemView.findViewById(R.id.checkedTextView);
            mTextView = itemView.findViewById(R.id.title);
            mCheckedTextView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                mCheckedTextView.toggle();
                // the same normalised id the policy checks against (SEC-05)
                ParentalControl.setCategoryAllowed(mStore, mModel.get(position).getUrl(),
                        mCheckedTextView.isChecked());
            });
        }

        void bind(int position) {
            mTextView.setEnabled(enabled);
            mTextView.setText(mModel.get(position).getSorceName());
            if (position == 0) {
                mTextView.setVisibility(View.VISIBLE);
            } else if (!mModel.get(position).getSorceName().equals(mModel.get(position - 1).getSorceName())) {
                mTextView.setVisibility(View.VISIBLE);
            } else {
                mTextView.setVisibility(View.GONE);
            }

            mCheckedTextView.setText(mModel.get(position).getName());
            mCheckedTextView.setChecked(ParentalControl.isCategoryAllowed(mStore, mModel.get(position).getUrl()));
            mCheckedTextView.setEnabled(enabled);
        }
    }

    private ArrayList<ParentControlPOJO> mModel;

    private final ParentalControl.Store mStore;

    private boolean enabled = false;

    public ParentalControlAddapter(@NonNull ParentalControl.Store store) {
        mModel = new ArrayList<>();
        mStore = store;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyItemRangeChanged(0, getItemCount());
    }

    public ParentControlPOJO getItem(int position) {
        return mModel.get(position);
    }

    public void clearItem() {
        int oldSize = mModel.size();
        if (oldSize > 0) {
            mModel.clear();
            notifyItemRangeRemoved(0, oldSize);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(i);
    }


    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.parental_control_recycler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<ParentControlPOJO> newModel) {
        mModel = newModel == null ? new ArrayList<>() : new ArrayList<>(newModel);
        notifyDataSetChanged();
    }


    @Override
    public int getItemCount() {
        return mModel.size();
    }
}
