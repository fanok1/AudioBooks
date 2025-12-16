package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.SearchebleArrayPOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class SearchebleAdapter extends RecyclerView.Adapter<SearchebleAdapter.MyHolder> {

    private ArrayList<SearchebleArrayPOJO> mModel;

    private SearchebleAdapter.OnListItemSelectedInterface mListener;

    public void setListener(
            SearchebleAdapter.OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<SearchebleArrayPOJO> newModel) {
        mModel = newModel;
        notifyDataSetChanged();
    }


    public void clearItem() {
        if (mModel != null) {
            int oldSize = mModel.size();
            if (oldSize > 0) {
                mModel.clear();
                notifyItemRangeRemoved(0, oldSize);
            }
        }
    }


    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.simple_row, viewGroup, false);
        return new SearchebleAdapter.MyHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i));
    }

    @Override
    public int getItemCount() {
        if (mModel == null) {
            return 0;
        } else {
            return mModel.size();
        }
    }

    public interface OnListItemSelectedInterface {
        void onItemSelected(View view, int position);
    }

    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mTextView;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mTextView = itemView.findViewById(R.id.textView);
            itemView.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getBindingAdapterPosition());
            });
        }

        void bind(SearchebleArrayPOJO searchebleArrayPOJO) {
            mTextView.setText(searchebleArrayPOJO.getName());
        }
    }
}
