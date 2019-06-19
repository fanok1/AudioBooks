package com.fanok.audiobooks.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class BooksListAddapter extends RecyclerView.Adapter<BooksListAddapter.MyHolder> {

    private ArrayList<BookPOJO> mModel;

    public void setItem(ArrayList<BookPOJO> model) {
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
                R.layout.books_recycler_item, viewGroup, false);
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

    class MyHolder extends RecyclerView.ViewHolder {

        private ImageView mImageView;
        private TextView mTitle;
        private TextView mGenre;
        private TextView mReting;
        private TextView mFavorite;
        private TextView mComents;
        private TextView mSiresle;
        private TextView mTime;
        private TextView mAutor;
        private TextView mArtist;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.title);
            mGenre = itemView.findViewById(R.id.genre);
            mReting = itemView.findViewById(R.id.reting);
            mFavorite = itemView.findViewById(R.id.favorite);
            mComents = itemView.findViewById(R.id.coments);
            mSiresle = itemView.findViewById(R.id.series);
            mTime = itemView.findViewById(R.id.time);
            mAutor = itemView.findViewById(R.id.autor);
            mArtist = itemView.findViewById(R.id.artist);
        }

        void bind(BookPOJO book) {
            if (book.getName() == null || book.getGenre() == null || book.getAutor() == null ||
                    book.getArtist() == null || book.getUrl() == null || book.getUrlArtist() == null
                    ||
                    book.getUrlAutor() == null || book.getUrlGenre() == null) {
                throw new NullPointerException();
            }
            Picasso.get().load(book.getPhoto()).into(mImageView);
            mTitle.setText(book.getName());
            mGenre.setText(book.getGenre());
            mReting.setText(book.getReting());
            mFavorite.setText(String.valueOf(book.getFavorite()));
            mComents.setText(String.valueOf(book.getComents()));
            mAutor.setText(book.getAutor());
            mArtist.setText(book.getArtist());
            if (book.getTime() != null && !book.getTime().isEmpty()) {
                mTime.setText(book.getTime());
                mTitle.setVisibility(View.VISIBLE);
            } else {
                mTitle.setVisibility(View.GONE);
            }
            if (book.getSeries() != null && book.getUrlSeries() != null &&
                    !book.getSeries().isEmpty() && !book.getUrlSeries().isEmpty()) {
                mSiresle.setText(book.getSeries());
                mSiresle.setVisibility(View.VISIBLE);
            } else {
                mSiresle.setVisibility(View.GONE);
            }
        }
    }
}
