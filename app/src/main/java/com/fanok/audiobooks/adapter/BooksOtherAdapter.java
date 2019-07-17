package com.fanok.audiobooks.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class BooksOtherAdapter extends RecyclerView.Adapter<BooksOtherAdapter.ViewHolder> {
    private static final String TAG = "BooksOtherAdapter";

    private ArrayList<BookPOJO> mData;
    private Context mContext;

    public BooksOtherAdapter(Context context) {
        mData = new ArrayList<>();
        mContext = context;
    }

    public void setData(@NonNull ArrayList<BookPOJO> data) {
        mData = data;
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        Log.d(TAG, "onCreateViewHolder: called");
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.slider_item,
                viewGroup, false);
        view.setOnClickListener(view1 -> {
            Intent intent = new Intent(mContext, LoadBook.class);
            intent.putExtra("url", mData.get(i).getUrl());
            mContext.startActivity(intent);

        });
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Log.d(TAG, "onBindViewHolder: called");
        Picasso.get().load(mData.get(i).getPhoto()).into(viewHolder.mImageView);
        viewHolder.mTitle.setText(mData.get(i).getName());
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private ImageView mImageView;
        private TextView mTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.text);


        }
    }
}
