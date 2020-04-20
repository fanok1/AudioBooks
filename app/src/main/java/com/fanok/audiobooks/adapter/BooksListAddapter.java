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
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.github.lzyzsd.circleprogress.DonutProgress;

import java.io.File;
import java.util.ArrayList;

public class BooksListAddapter extends RecyclerView.Adapter<BooksListAddapter.MyHolder> {

    private static final String TAG = "BooksListAddapter";

    private ArrayList<BookPOJO> mModel;

    private OnListItemSelectedInterface mListener;
    private OnListItemLongSelectedInterface mLongListener;
    private AudioListDBModel mAudioListDBModel;
    private AudioDBModel mAudioDBModel;
    private boolean procent;


    public BooksListAddapter() {
        procent = false;
    }

    public BooksListAddapter(boolean showProcent) {
        this.procent = showProcent;
    }

    public void close() {
        if (mAudioListDBModel != null) {
            mAudioListDBModel.closeDB();
        }
        if (mAudioDBModel != null) {
            mAudioDBModel.closeDB();
        }
    }

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
        if (mModel != null) {
            mModel = new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.books_recycler_item, viewGroup, false);
        UiModeManager uiModeManager = (UiModeManager) viewGroup.getContext().getSystemService(
                UI_MODE_SERVICE);
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(viewGroup.getContext());
        String listType = preferences.getString("books_adapter_layout_pref",
                viewGroup.getContext().getString(R.string.books_adapter_layout_list_value));
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            if (listType.equals(
                    viewGroup.getContext().getString(R.string.books_adapter_layout_list_value))) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.books_recycler_item_television, viewGroup, false);
            } else if (listType.equals(viewGroup.getContext().getString(
                    R.string.books_adapter_layout_big_list_value))) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.books_recycler_item_big_lelevision, viewGroup, false);
            }
        } else {
            if (listType.equals(
                    viewGroup.getContext().getString(R.string.books_adapter_layout_list_value))) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.books_recycler_item, viewGroup, false);
            } else if (listType.equals(viewGroup.getContext().getString(
                    R.string.books_adapter_layout_big_list_value))) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.books_recycler_item_big, viewGroup, false);
            }
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
        private ImageView mIsDownload;
        private TextView mTitle;
        private TextView mGenre;
        private TextView mReting;
        private TextView mComents;
        private TextView mSiresle;
        private TextView mTime;
        private TextView mAutor;
        private TextView mArtist;
        private TextView mSource;
        private DonutProgress mDonutProgress;

        private SharedPreferences mPreferences;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mIsDownload = itemView.findViewById(R.id.is_download);
            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.title);
            mGenre = itemView.findViewById(R.id.genre);
            mReting = itemView.findViewById(R.id.reting);
            mComents = itemView.findViewById(R.id.coments);
            mSiresle = itemView.findViewById(R.id.series);
            mTime = itemView.findViewById(R.id.time);
            mAutor = itemView.findViewById(R.id.autor);
            mArtist = itemView.findViewById(R.id.artist);
            LinearLayout linearLayout = itemView.findViewById(R.id.contentConteiner);
            mSource = itemView.findViewById(R.id.source);
            mDonutProgress = itemView.findViewById(R.id.donutProgress);
            mPreferences = mArtist.getContext().getSharedPreferences(PARENTAL_CONTROL_PREFERENCES,
                    MODE_PRIVATE);


            linearLayout.setOnClickListener(view -> {
                if (mListener != null) {
                    mListener.onItemSelected(view,
                            getAdapterPosition());
                }
            });


            linearLayout.setOnLongClickListener(view -> {
                if (mLongListener != null) {
                    mLongListener.onItemLongSelected(view,
                            getAdapterPosition());
                }
                return true;
            });

            mAudioDBModel = new AudioDBModel(itemView.getContext().getApplicationContext());
            mAudioListDBModel = new AudioListDBModel(itemView.getContext().getApplicationContext());

        }


        void bind(BookPOJO book) {
            if (book.getName() == null || book.getUrl() == null) {
                throw new NullPointerException();
            }

            if (mPreferences.getBoolean(PARENTAL_CONTROL_ENABLED, false) && (book.getGenre() == null
                    || book.getGenre().isEmpty() ||
                    !mPreferences.getBoolean(book.getGenre(), false))) {
                Glide.with(mImageView).load(R.drawable.ic_parental_control).into(mImageView);

            } else if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
                Glide.with(mImageView).load(book.getPhoto())
                        .thumbnail(0.1f)
                        .override(mImageView.getWidth(), mImageView.getHeight()).into(
                        mImageView);
            }

            if (book.getUrl().contains("knigavuhe.org")) {
                mSource.setText(R.string.kniga_v_uhe);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains("izibuk.ru")) {
                mSource.setText(R.string.izibuc);
                mSource.setVisibility(View.VISIBLE);
            } else {
                mSource.setVisibility(View.GONE);
            }


            mTitle.setText(book.getName());

            if (book.getGenre() != null && !book.getGenre().isEmpty()) {
                mGenre.setVisibility(View.VISIBLE);
                mGenre.setText(book.getGenre());
            } else {
                mGenre.setVisibility(View.GONE);
            }
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
                mTime.setVisibility(View.VISIBLE);
            } else {
                mTime.setVisibility(View.GONE);
            }
            if (book.getSeries() != null && book.getUrlSeries() != null &&
                    !book.getSeries().isEmpty() && !book.getUrlSeries().isEmpty()) {
                mSiresle.setText(book.getSeries());
                mSiresle.setVisibility(View.VISIBLE);
            } else {
                mSiresle.setVisibility(View.GONE);
            }

            if (procent) {
                if (mAudioDBModel != null && mAudioListDBModel != null) {
                    ArrayList<AudioListPOJO> audioListPOJOS;
                    audioListPOJOS = mAudioListDBModel.get(book.getUrl());
                    if (audioListPOJOS.isEmpty()) {
                        setVisible(false);
                    } else {
                        String last = mAudioDBModel.getName(book.getUrl());
                        if (last == null || last.isEmpty()) {
                            mDonutProgress.setText("0%");
                            mDonutProgress.setProgress(0);
                        } else {
                            int timeCurent = 0;
                            int timeDuration = 0;
                            boolean b = true;
                            for (AudioListPOJO pojo : audioListPOJOS) {
                                if (b) {
                                    if (pojo.getAudioName().equals(last)) {
                                        b = false;
                                        timeCurent += mAudioDBModel.getTime(book.getUrl());
                                    } else {
                                        timeCurent += pojo.getTime();
                                    }
                                }
                                timeDuration += pojo.getTime();

                            }
                            int procent = timeCurent * 100 / timeDuration;
                            mDonutProgress.setProgress(procent);
                            mDonutProgress.setText(procent + "%");
                        }
                        setVisible(true);
                    }

                } else {
                    setVisible(false);
                }
            } else {
                setVisible(false);
            }

            File[] folders = mIsDownload.getContext().getExternalFilesDirs(null);
            int size = 0;
            if (mAudioListDBModel != null) {
                ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(book.getUrl());
                for (File folder : folders) {
                    if (folder != null) {
                        File dir = new File(folder.getAbsolutePath() + "/" + book.getName());
                        if (dir.exists() && dir.isDirectory()) {
                            for (AudioListPOJO pojo : arrayList) {
                                String url = pojo.getAudioUrl();
                                File file = new File(dir, url.substring(url.lastIndexOf("/") + 1));
                                if (file.exists()) {
                                    size++;
                                }
                            }
                        }
                    }
                }
                if (size == 0) {
                    mIsDownload.setVisibility(View.GONE);
                } else {
                    if (size >= arrayList.size()) {
                        mIsDownload.setImageDrawable(
                                mIsDownload.getContext().getDrawable(R.drawable.ic_check_all));
                    } else {
                        mIsDownload.setImageDrawable(
                                mIsDownload.getContext().getDrawable(R.drawable.ic_check_1));
                    }
                    mIsDownload.setVisibility(View.VISIBLE);
                }
            } else {
                mIsDownload.setVisibility(View.GONE);
            }


        }

        private void setVisible(boolean b) {
            if (b) {
                mImageView.setColorFilter(
                        ContextCompat.getColor(mImageView.getContext(), R.color.imageTint));
                mDonutProgress.setVisibility(View.VISIBLE);
            } else {
                mDonutProgress.setVisibility(View.GONE);
                mImageView.setColorFilter(ContextCompat.getColor(mImageView.getContext(),
                        android.R.color.transparent));
            }
        }
    }
}
