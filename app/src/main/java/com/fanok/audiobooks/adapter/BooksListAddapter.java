package com.fanok.audiobooks.adapter;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.UI_MODE_SERVICE;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
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
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.util.DownloadUtil;
import com.github.lzyzsd.circleprogress.DonutProgress;
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
    private final android.os.Handler mainHandler = new android.os.Handler(Looper.getMainLooper());
    private final java.util.Map<String, Integer> progressCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> downloadCache = new java.util.concurrent.ConcurrentHashMap<>();
    @UnstableApi
    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mArtist;

        private final TextView mAutor;

        private final TextView mComents;

        private final DonutProgress mDonutProgress;

        /** The percent with its "listened" label inside the ring; null where the ring draws the percent (TV). */
        private final View mListened;

        private final TextView mListenedPercent;

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
            mListened = itemView.findViewById(R.id.listened);
            mListenedPercent = itemView.findViewById(R.id.listenedPercent);
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

            android.graphics.drawable.Drawable mask =
                    com.fanok.audiobooks.policy.ParentalControl.coverMask(mImageView.getContext(), book);
            if (mask != null) {
                Glide.with(mImageView).load(mask).into(mImageView);

            } else {
                if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
                    String photoPath = book.getPhoto();
                    if (com.fanok.audiobooks.policy.DefaultCover.replaces(photoPath)) {
                        Glide.with(mImageView).load(com.fanok.audiobooks.policy.DefaultCover.drawable(
                                mImageView.getContext(), book.getUrl())).into(mImageView);
                    } else if (photoPath.startsWith("content://")) {
                        Glide.with(mImageView).load(android.net.Uri.parse(photoPath)).into(mImageView);
                    } else if (photoPath.startsWith("/") || photoPath.contains(":\\")) {
                        Glide.with(mImageView).load(new java.io.File(photoPath)).into(mImageView);
                    } else {
                        Glide.with(mImageView).load(com.fanok.audiobooks.net.Covers.glide(photoPath)).into(mImageView);
                    }
                } else {
                    mImageView.setImageDrawable(
                            ContextCompat.getDrawable(mImageView.getContext(), android.R.drawable.ic_menu_gallery));
                }
            }

            Provider provider = Provider.ofUrl(book.getUrl());
            if (provider != null) {
                mSource.setText(provider.title);
                mSource.setVisibility(View.VISIBLE);
            } else {
                mSource.setVisibility(View.GONE);
            }


            String titleStr = book.getName();
            if (titleStr != null) {
                mTitle.setText(titleStr.trim().replace("\n", " "));
            } else {
                mTitle.setText("");
            }

            if (book.getGenre() != null && !book.getGenre().isEmpty()) {
                mGenre.setVisibility(View.VISIBLE);
                mGenre.setText(book.getGenre());
            } else {
                mGenre.setVisibility(View.GONE);
            }
            String retingStr = book.getReting();
            if (retingStr != null && !retingStr.equals("0")) {
                mReting.setText(retingStr);
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
                mTime.setText(Consts.formatTimeDisplay(book.getTime()));
                mTime.setVisibility(View.VISIBLE);
            } else {
                mTime.setVisibility(View.GONE);
            }
            if (book.getSeries() != null && !book.getSeries().isEmpty()) {
                android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(" \u00A0 " + book.getSeries());
                android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_book);
                if (drawable != null) {
                    drawable.setBounds(0, 0, (int)mSiresle.getTextSize(), (int)mSiresle.getTextSize());
                    android.text.style.ImageSpan imageSpan = new android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_BOTTOM);
                    ssb.setSpan(imageSpan, 0, 1, android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                }
                mSiresle.setText(ssb);
                mSiresle.setVisibility(View.VISIBLE);
            } else {
                mSiresle.setVisibility(View.GONE);
            }

            if (procent) {
                Integer pct = progressCache.get(book.getUrl());
                if (pct == null || pct == -1) {
                    setVisible(false);
                } else {
                    mDonutProgress.setProgress(pct);
                    mDonutProgress.setText(pct + "%");
                    if (mListenedPercent != null) mListenedPercent.setText(pct + "%");
                    setVisible(true);
                }
            } else {
                setVisible(false);
            }

            boolean isImported = book != null && book.getUrl() != null && book.getUrl().contains("/imported/");
            Integer dlStatus = downloadCache.get(book.getUrl());
            if (isImported || dlStatus == null || dlStatus == 0) {
                mIsDownload.setVisibility(View.GONE);
            } else if (dlStatus == 1) {
                mIsDownload.setImageDrawable(
                        ContextCompat.getDrawable(mIsDownload.getContext(), R.drawable.ic_check_1));
                mIsDownload.setContentDescription(mIsDownload.getContext().getString(R.string.download_state_partial));
                mIsDownload.setVisibility(View.VISIBLE);
            } else {
                mIsDownload.setImageDrawable(
                        ContextCompat.getDrawable(mIsDownload.getContext(), R.drawable.ic_check_all));
                mIsDownload.setContentDescription(mIsDownload.getContext().getString(R.string.download_state_full));
                mIsDownload.setVisibility(View.VISIBLE);
            }


        }

        private void setVisible(boolean b) {
            if (mListened != null) mListened.setVisibility(b ? View.VISIBLE : View.GONE);
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
        // a copy: the presenter keeps appending to its own list, the adapter must not see that
        mModel = newModel == null ? new ArrayList<>() : new ArrayList<>(newModel);
        preloadStatuses(mModel);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refreshItem() {
        notifyDataSetChanged();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void preloadStatuses(ArrayList<BookPOJO> newModel) {
        if (newModel == null || newModel.isEmpty()) {
            progressCache.clear();
            downloadCache.clear();
            return;
        }
        executor.execute(() -> {
            BooksDBModel dbModel = null;
            AudioListDBModel audioListModel = null;
            AudioDBModel audioModel = null;
            DownloadIndex downloadIndex = null;
            try {
                Context ctx = App.getInstance();
                dbModel = new BooksDBModel(ctx);
                audioListModel = new AudioListDBModel(ctx);
                audioModel = new AudioDBModel(ctx);
                downloadIndex = DownloadUtil.getDownloadManager().getDownloadIndex();

                for (BookPOJO b : newModel) {
                    String url = b.getUrl();
                    if (url == null) continue;

                    boolean saved = dbModel.inSaved(url);

                    int progress = -1;
                    if (procent && audioListModel != null) {
                        ArrayList<AudioListPOJO> pos = audioListModel.get(url);
                        if (pos.isEmpty()) {
                            progress = -1;
                        } else {
                            String last = audioModel.getName(url);
                            if (last == null || last.isEmpty()) {
                                progress = 0;
                            } else {
                                int current = 0;
                                int total = 0;
                                boolean bFound = false;
                                for (AudioListPOJO po : pos) {
                                    if (!bFound) {
                                        if (po.getAudioName().equals(last)) {
                                            bFound = true;
                                            current += audioModel.getTime(url);
                                        } else {
                                            current += po.getTime();
                                        }
                                    }
                                    total += po.getTime();
                                }
                                if (total != 0) {
                                    progress = current * 100 / total;
                                } else {
                                    progress = 0;
                                }
                            }
                        }
                    }
                    progressCache.put(url, progress);

                    int dl = 0;
                    if (saved) {
                        if (url.contains("/imported/")) {
                            dl = 2;
                        } else if (audioListModel != null) {
                            ArrayList<AudioListPOJO> list = audioListModel.get(url);
                            if (list != null && !list.isEmpty()) {
                                int count = 0;
                                // A chapter counts as downloaded when its row records an app-written copy, when it is an
                                // imported local file, or when its Media3 download (resolved by chapter identity) is complete.
                                java.util.Set<String> withCopy = new java.util.HashSet<>();
                                for (com.fanok.audiobooks.room.BooksAudioEntity row : audioListModel.getRows(url)) {
                                    if (row.localUri != null && !row.localUri.isEmpty()) withCopy.add(row.urlAudio);
                                }
                                java.util.Map<String, Download> downloads = java.util.Collections.emptyMap();
                                if (downloadIndex != null) {
                                    java.util.List<String> urls = new java.util.ArrayList<>();
                                    for (AudioListPOJO pojo : list) {
                                        if (pojo != null && pojo.getAudioUrl() != null) urls.add(pojo.getAudioUrl());
                                    }
                                    try {
                                        downloads = DownloadUtil.downloadsByChapterUrl(ctx, downloadIndex, url, urls);
                                    } catch (Exception ignored) {}
                                }
                                for (AudioListPOJO pojo : list) {
                                    if (pojo == null) continue;
                                    String aUrl = pojo.getAudioUrl();
                                    boolean isTrackDownloaded = withCopy.contains(aUrl);
                                    if (!isTrackDownloaded && aUrl != null && !aUrl.isEmpty()) {
                                        if (aUrl.startsWith("content://") || aUrl.startsWith("file://")) {
                                            isTrackDownloaded = true;
                                        } else if (aUrl.startsWith("/")) {
                                            isTrackDownloaded = new java.io.File(aUrl).exists();
                                        }
                                    }
                                    if (!isTrackDownloaded) {
                                        Download d = downloads.get(aUrl);
                                        isTrackDownloaded = d != null && d.state == Download.STATE_COMPLETED;
                                    }
                                    if (isTrackDownloaded) {
                                        count++;
                                    }
                                }

                                if (count >= list.size() && count > 0) {
                                    dl = 2;
                                } else if (count > 0) {
                                    dl = 1;
                                } else {
                                    dl = 0;
                                }
                            } else {
                                dl = 0;
                            }
                        } else {
                            dl = 0;
                        }
                    } else {
                        dl = 0;
                    }
                    downloadCache.put(url, dl);
                }
            } catch (Exception e) {
                android.util.Log.e("ADAPT", "prefetch error", e);
            } finally {
                if (dbModel != null) dbModel.closeDB();
                if (audioListModel != null) audioListModel.closeDB();
                if (audioModel != null) audioModel.closeDB();
            }
            mainHandler.post(() -> {
                if (mModel != null) {
                    notifyDataSetChanged();
                }
            });
        });
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
