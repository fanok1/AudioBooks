package com.fanok.audiobooks.adapter;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.UI_MODE_SERVICE;

import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_ENABLED;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;

import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;

import java.util.ArrayList;

public class BooksListAddapter extends RecyclerView.Adapter<BooksListAddapter.MyHolder> {

    private static final String TAG = "BooksListAddapter";

    private ArrayList<BookPOJO> mModel;

    private OnListItemSelectedInterface mListener;
    private OnListItemLongSelectedInterface mLongListener;

    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    public void setLongListener(
            OnListItemLongSelectedInterface longListener) {
        mLongListener = longListener;
    }

    public interface OnListItemLongSelectedInterface {
        void onItemLongSelected(View view, int position);
    }

    public interface OnListItemSelectedInterface {
        void onItemSelected(View view, int position);
    }

    public void setItem(ArrayList<BookPOJO> model) {
        if (mModel != model) {
            mModel = model;
        }
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view;
        UiModeManager uiModeManager = (UiModeManager) viewGroup.getContext().getSystemService(
                UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.books_recycler_item_television, viewGroup, false);
        } else {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.books_recycler_item, viewGroup, false);
        }
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
        private TextView mComents;
        private TextView mSiresle;
        private TextView mTime;
        private TextView mAutor;
        private TextView mArtist;
        private LinearLayout mLinearLayout;

        private SharedPreferences mPreferences;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.title);
            mGenre = itemView.findViewById(R.id.genre);
            mReting = itemView.findViewById(R.id.reting);
            mComents = itemView.findViewById(R.id.coments);
            mSiresle = itemView.findViewById(R.id.series);
            mTime = itemView.findViewById(R.id.time);
            mAutor = itemView.findViewById(R.id.autor);
            mArtist = itemView.findViewById(R.id.artist);
            mLinearLayout = itemView.findViewById(R.id.contentConteiner);
            mPreferences = mArtist.getContext().getSharedPreferences(PARENTAL_CONTROL_PREFERENCES,
                    MODE_PRIVATE);


            mLinearLayout.setOnClickListener(view -> {
                if (mListener != null) {
                    mListener.onItemSelected(view,
                            getAdapterPosition());
                }
            });


            mLinearLayout.setOnLongClickListener(view -> {
                if (mLongListener != null) {
                    mLongListener.onItemLongSelected(view,
                            getAdapterPosition());
                }
                return true;
            });

        }


        void bind(BookPOJO book) {
            if (book.getName() == null || book.getGenre() == null || book.getAutor() == null ||
                    book.getArtist() == null || book.getUrl() == null || book.getUrlArtist() == null
                    || book.getUrlGenre() == null) {
                throw new NullPointerException();
            }

            if (mPreferences.getBoolean(PARENTAL_CONTROL_ENABLED, false) &&
                    !mPreferences.getBoolean(book.getGenre(), false)) {
                Glide.with(mImageView).load(R.drawable.ic_parental_control).into(mImageView);

            } else {
                Glide.with(mImageView).load(book.getPhoto())
                        .thumbnail(0.1f)
                        .override(mImageView.getWidth(), mImageView.getHeight()).into(mImageView);
            }

            mTitle.setText(book.getName());
            mGenre.setText(book.getGenre());
            if (!book.getReting().equals("0")) {
                mReting.setText(book.getReting());
                mReting.setVisibility(View.VISIBLE);
            } else {
                mReting.setVisibility(View.GONE);
            }

            if (book.getComents() != 0) {
                mComents.setText(String.valueOf(book.getComents()));
                mComents.setVisibility(View.VISIBLE);
            } else {
                mComents.setVisibility(View.GONE);
            }

            if (book.getAutor() != null && !book.getAutor().isEmpty()) {
                mAutor.setText(book.getAutor());
                mAutor.setVisibility(View.VISIBLE);
            } else {
                mAutor.setVisibility(View.GONE);
            }
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
