package com.fanok.audiobooks.adapter;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import java.util.ArrayList;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mText;

        private final View mView;


        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.textView);
            mView = itemView;
            itemView.setOnClickListener(view -> {
                int pos = getAdapterPosition();
                if (mListener != null) {
                    mListener.onItemSelected(view, pos);
                }
                selected = pos;
                notifyDataSetChanged();
            });


        }

        void bind(int pos) {
            if (pos == selected) {
                mView.setBackgroundResource(R.drawable.filter_selected);
            } else {
                mView.setBackgroundColor(
                        mView.getContext().getResources().getColor(android.R.color.transparent));
            }

            switch (mData.get(pos)) {
                case -1:
                    mText.setText(R.string.all);
                    break;
                case Consts.SOURCE_KNIGA_V_UHE:
                    mText.setText(R.string.kniga_v_uhe);
                    break;
                case Consts.SOURCE_IZI_BUK:
                    mText.setText(R.string.izibuc);
                    break;
                case Consts.SOURCE_AUDIO_BOOK_MP3:
                    mText.setText(R.string.audionook_mp3);
                    break;
                case Consts.SOURCE_ABOOK:
                    mText.setText(R.string.abook);
                    break;
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

        if (preferences.getBoolean("search_abook", true)) {
            mData.add(Consts.SOURCE_ABOOK);
        }

        if (preferences.getBoolean("search_kniga_v_uhe", true)) {
            mData.add(Consts.SOURCE_KNIGA_V_UHE);
        }

        if (preferences.getBoolean("search_izibuc", true)) {
            mData.add(Consts.SOURCE_IZI_BUK);
        }

        if (preferences.getBoolean("search_abmp3", true)) {
            mData.add(Consts.SOURCE_AUDIO_BOOK_MP3);
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
