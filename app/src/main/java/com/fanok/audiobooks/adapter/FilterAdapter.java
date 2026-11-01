package com.fanok.audiobooks.adapter;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.search.Sources;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mText;

        private final View mView;


        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.textView);
            mView = itemView;
            itemView.setOnClickListener(view -> {
                int newPosition = getBindingAdapterPosition();
                if (newPosition != RecyclerView.NO_POSITION && newPosition != selected) {
                    notifyItemChanged(selected);
                    selected = newPosition;
                    notifyItemChanged(selected);
                    if (mListener != null) {
                        mListener.onItemSelected(view, newPosition);
                    }
                }
            });


        }

        void bind(int pos) {
            if (pos == selected) {
                mView.setBackgroundResource(R.drawable.filter_selected);
            } else {
                mView.setBackgroundResource(R.drawable.filter_item_selector);
            }

            Provider provider = Provider.ofSource(mData.get(pos));
            if (provider == null) {
                mText.setText(R.string.all);
            } else {
                mText.setText(provider.title);
            }
        }
    }

    public interface OnListItemSelectedInterface {

        void onItemSelected(View view, int position);
    }

    private final ArrayList<Integer> mData;

    private OnListItemSelectedInterface mListener;

    private int selected = 0;


    public FilterAdapter(SharedPreferences preferences) {
        mData = new ArrayList<>();
        mData.add(-1);
        for (Provider provider : Sources.active()) {
            if (preferences.getBoolean(provider.prefKey, true)) {
                mData.add(provider.sourceId);
            }
        }
    }

    public int getItem(int postion) {
        return mData.get(postion);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(i);
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.simple_row_3, viewGroup, false);
        return new MyHolder(view);
    }

    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
