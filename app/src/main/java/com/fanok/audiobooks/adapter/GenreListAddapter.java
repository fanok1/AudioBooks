package com.fanok.audiobooks.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.GenrePOJO;

import java.util.ArrayList;

public class GenreListAddapter extends RecyclerView.Adapter<GenreListAddapter.MyHolder> {

    private RecyclerTushListner listner;
    private ArrayList<GenrePOJO> mModel;

    public void setClickListner(RecyclerTushListner listner) {
        this.listner = listner;
    }

    public void setItem(ArrayList<GenrePOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.genre_recycler_item, viewGroup, false);

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


    public interface RecyclerTushListner {
        void onClickItem(View view, int position);
    }

    class MyHolder extends RecyclerView.ViewHolder {

        private TextView mName;
        private TextView mReting;
        private TextView mDescription;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mName = itemView.findViewById(R.id.name);
            mReting = itemView.findViewById(R.id.reting);
            mDescription = itemView.findViewById(R.id.desc);
            itemView.setOnClickListener(view -> {
                if (listner != null) listner.onClickItem(itemView, getAdapterPosition());
            });
        }

        void bind(GenrePOJO book) {
            if (book.getName() == null || book.getUrl() == null) {
                throw new NullPointerException();
            }
            mName.setText(book.getName());
            if (book.getReting() != 0) {
                mReting.setText(String.valueOf(book.getReting()));
            } else {
                mReting.setVisibility(View.GONE);
            }
            mDescription.setText(book.getDescription());
        }
    }
}
