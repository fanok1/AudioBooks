package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class OtherArtistListAddapter extends
        RecyclerView.Adapter<OtherArtistListAddapter.MyHolder> {

    private ArrayList<OtherArtistPOJO> mModel;

    private OnListItemSelectedInterface mListener;


    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<OtherArtistPOJO> newModel) {
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

    public OtherArtistPOJO getItem(int postion) {
        return mModel.get(postion);
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.simple_row_2, viewGroup, false);
        return new MyHolder(view);
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


        private final TextView mText;


        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.text);

            itemView.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getBindingAdapterPosition());
            });


        }

        void bind(OtherArtistPOJO book) {
            mText.setText(book.getName());
        }
    }
}
