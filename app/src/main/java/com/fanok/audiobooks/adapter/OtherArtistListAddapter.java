package com.fanok.audiobooks.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;

import java.util.ArrayList;

public class OtherArtistListAddapter extends
        RecyclerView.Adapter<OtherArtistListAddapter.MyHolder> {

    private ArrayList<OtherArtistPOJO> mModel;

    private OnListItemSelectedInterface mListener;


    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    public void setItem(ArrayList<OtherArtistPOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
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


        private TextView mText;


        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.text);

            itemView.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getAdapterPosition());
            });


        }

        void bind(OtherArtistPOJO book) {
            mText.setText(book.getName());
        }
    }
}
