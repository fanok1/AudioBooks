package com.fanok.audiobooks.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Log.d(TAG, "onBindViewHolder: called");
        Picasso.get().load(mData.get(i).getPhoto()).into(viewHolder.mImageView);
        viewHolder.mTitle.setText(mData.get(i).getName());
        viewHolder.mImageView.setOnClickListener(
                view -> myOnClick(viewHolder.getAdapterPosition()));
        viewHolder.mTitle.setOnClickListener(view -> myOnClick(viewHolder.getAdapterPosition()));

        /*//translation
        String lang = Locale.getDefault().toLanguageTag();
        if(!lang.equals("ru")) {
            FirebaseTranslatorOptions options =
                    new FirebaseTranslatorOptions.Builder()
                            .setSourceLanguage(FirebaseTranslateLanguage.RU)
                            .setTargetLanguage(FirebaseTranslateLanguage.languageForLanguageCode
                            (lang))
                            .build();
            final FirebaseTranslator translator =
                    FirebaseNaturalLanguage.getInstance().getTranslator(options);

            FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions
            .Builder()
                    .requireWifi()
                    .build();
            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(
                            v -> {
                                translator.translate(mData.get(i).getName())
                                        .addOnSuccessListener(
                                                translatedText -> viewHolder.mTitle.setText
                                                (translatedText));
                            });
        }*/

    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    private void myOnClick(int i) {
        Intent intent = new Intent(mContext, LoadBook.class);
        intent.putExtra("url", mData.get(i).getUrl());
        mContext.startActivity(intent);
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