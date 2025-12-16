package com.fanok.audiobooks.adapter;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.UI_MODE_SERVICE;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_ENABLED;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.util.DownloadUtil;
import com.github.lzyzsd.circleprogress.DonutProgress;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** @noinspection ClassEscapesDefinedScope*/
public class BooksListAddapter extends RecyclerView.Adapter<BooksListAddapter.MyHolder> {

    private ArrayList<BookPOJO> mModel;

    private OnListItemSelectedInterface mListener;
    private OnListItemLongSelectedInterface mLongListener;
    private AudioListDBModel mAudioListDBModel;
    private AudioDBModel mAudioDBModel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @UnstableApi
    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mArtist;

        private final TextView mAutor;

        private final TextView mComents;

        private final DonutProgress mDonutProgress;

        private final TextView mGenre;

        private final ImageView mImageView;

        private final ImageView mIsDownload;

        private final SharedPreferences mPreferences;

        private final TextView mReting;

        private final TextView mSiresle;

        private final TextView mSource;

        private final TextView mTime;

        private final TextView mTitle;


        @UnstableApi
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
                            getBindingAdapterPosition());
                }
            });


            linearLayout.setOnLongClickListener(view -> {
                if (mLongListener != null) {
                    mLongListener.onItemLongSelected(view,
                            getBindingAdapterPosition());
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
                    !mPreferences.getBoolean(book.getUrlGenre(), false))) {
                Glide.with(mImageView).load(R.drawable.ic_parental_control).into(mImageView);

            } else {
                if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
                    if(App.useProxy&&book.getPhoto().contains(Url.SERVER_BAZA_KNIG)){

                        final Bitmap[] bmp = {null};
                        Handler handler = new Handler(Looper.getMainLooper());
                        executor.execute(() -> {

                            try {
                                URL url = new URL(book.getPhoto());
                                Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress(Consts.PROXY_HOST, Consts.PROXY_PORT));
                                bmp[0] = BitmapFactory.decodeStream(url.openConnection(proxy).getInputStream());
                            } catch (IOException ignored) {
                            }
                            handler.post(() -> {
                                if(bmp[0] !=null) {
                                    mImageView.setImageBitmap(bmp[0]);
                                }
                            });
                        });


                    }else {
                        Glide.with(mImageView).load(book.getPhoto())
                                .override(mImageView.getWidth(), mImageView.getHeight()).into(
                                        mImageView);
                    }
                } else {
                    mImageView.setImageDrawable(
                            ContextCompat.getDrawable(mImageView.getContext(), android.R.drawable.ic_menu_gallery));
                }
            }

            if (book.getUrl().contains(Url.SERVER)) {
                mSource.setText(R.string.kniga_v_uhe);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains(Url.SERVER_IZIBUK)) {
                mSource.setText(R.string.izibuc);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains(Url.SERVER_ABMP3)) {
                mSource.setText(R.string.audionook_mp3);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains(Url.SERVER_AKNIGA)) {
                mSource.setText(R.string.abook);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains(Url.SERVER_BAZA_KNIG)) {
                mSource.setText(R.string.baza_knig);
                mSource.setVisibility(View.VISIBLE);
            } else if (book.getUrl().contains(Url.SERVER_KNIGOBLUD)) {
                mSource.setText(R.string.knigoblud);
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
                            setVisible(true);
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
                            if (timeDuration != 0) {
                                int procent = timeCurent * 100 / timeDuration;
                                mDonutProgress.setProgress(procent);
                                mDonutProgress.setText(procent + "%");
                                setVisible(true);
                            } else {
                                setVisible(false);
                            }
                        }
                    }

                } else {
                    setVisible(false);
                }
            } else {
                setVisible(false);
            }

            if (mAudioListDBModel != null) {
                ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(book.getUrl());
                if (!arrayList.isEmpty()) {
                    DownloadIndex downloadIndex = DownloadUtil.getDownloadManager().getDownloadIndex();
                    int downloadedCount = 0;
                    try {
                        for (AudioListPOJO pojo : arrayList) {
                            Download download = downloadIndex.getDownload(pojo.getCleanAudioUrl());
                            if (download != null && download.state == Download.STATE_COMPLETED) {
                                downloadedCount++;
                            }
                        }
                    } catch (IOException e) {
                        // ignore
                    }

                    if (downloadedCount == 0) {
                        mIsDownload.setVisibility(View.GONE);
                    } else {
                        if (downloadedCount >= arrayList.size()) {
                            mIsDownload.setImageDrawable(
                                    ContextCompat.getDrawable(mIsDownload.getContext(), R.drawable.ic_check_all));
                        } else {
                            mIsDownload.setImageDrawable(
                                    ContextCompat.getDrawable(mIsDownload.getContext(), R.drawable.ic_check_1));
                        }
                        mIsDownload.setVisibility(View.VISIBLE);
                    }
                } else {
                    mIsDownload.setVisibility(View.GONE);
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


    public interface OnListItemLongSelectedInterface {

        void onItemLongSelected(View view, int position);
    }

    public interface OnListItemSelectedInterface {

        void onItemSelected(View view, int position);
    }

    private final boolean procent;

    public BooksListAddapter() {
        procent = false;
    }

    public BooksListAddapter(boolean showProcent) {
        this.procent = showProcent;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearItem() {
        if (mModel != null) {
            mModel = new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    public void close() {
        if (mAudioListDBModel != null) {
            mAudioListDBModel.closeDB();
        }
        if (mAudioDBModel != null) {
            mAudioDBModel.closeDB();
        }
    }

    @Override
    public int getItemCount() {
        if (mModel == null) {
            return 0;
        } else {
            return mModel.size();
        }
    }

    @UnstableApi
    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i));
    }

    @UnstableApi
    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.books_recycler_item, viewGroup, false);
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(viewGroup.getContext());
        String listType = preferences.getString("books_adapter_layout_pref",
                viewGroup.getContext().getString(R.string.books_adapter_layout_list_value));

        if (listType.equals(viewGroup.getContext().getString(R.string.books_adapter_layout_list_value))) {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.books_recycler_item, viewGroup, false);
        } else if (listType.equals(viewGroup.getContext().getString(
                R.string.books_adapter_layout_big_list_value))) {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.books_recycler_item_big, viewGroup, false);
        }

        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<BookPOJO> newModel) {
        mModel = newModel;
        notifyDataSetChanged();
    }

    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    public void setLongListener(
            OnListItemLongSelectedInterface longListener) {
        mLongListener = longListener;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
