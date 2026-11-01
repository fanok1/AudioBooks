package com.fanok.audiobooks.model;

import androidx.annotation.OptIn;
import static com.fanok.audiobooks.util.DownloadUtil.getDownloadManager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class FavoriteModel implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoriteModel {

    /** No chapter of the book is downloaded. */
    public static final int NOT_SAVED = -1;
    /** Some chapters are downloaded. */
    public static final int SAVED = 0;
    /** Every chapter is downloaded. */
    public static final int SAVED_ALL = 1;

    private final BooksDBModel mBooksDBModel;
    private final AudioListDBModel mAudioListDBModel;
    private final Context mContext;

    public FavoriteModel(Context context) {
        mBooksDBModel = new BooksDBModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mContext = context;
    }

    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(int table) {
        return Observable.fromCallable(() -> {
            ArrayList<BookPOJO> books;
            switch (table) {
                case Consts.TABLE_FAVORITE:
                    books = mBooksDBModel.getAllFavorite();
                    break;
                case Consts.TABLE_HISTORY:
                    books = mBooksDBModel.getAllHistory();
                    break;
                case Consts.TABLE_SAVED:
                    books = mBooksDBModel.getAllSaved();
                    break;
                default:
                    books = new ArrayList<>();
                    break;
            }

            if (table == Consts.TABLE_SAVED) {
                // only books that actually have a local copy; the rows stay (transient states must be safe)
                Map<String, Integer> states = savedStatesNow(books);
                ArrayList<BookPOJO> visible = new ArrayList<>();
                for (BookPOJO bookPOJO : books) {
                    Integer state = states.get(bookPOJO.getUrl());
                    if (state != null && state != NOT_SAVED) {
                        visible.add(bookPOJO);
                    }
                }
                books = visible;
            }
            return books;
        });
    }

    /** The download state of every book, computed once in one worker pass (DATA-07). */
    @Override
    public Single<Map<String, Integer>> savedStates(@NonNull List<BookPOJO> books) {
        final List<BookPOJO> copy = new ArrayList<>(books);
        return Single.fromCallable(() -> savedStatesNow(copy));
    }

    private Map<String, Integer> savedStatesNow(@NonNull List<BookPOJO> books) {
        Map<String, Integer> states = new HashMap<>();
        DownloadIndex downloadIndex = getDownloadManager().getDownloadIndex();
        for (BookPOJO book : books) {
            String url = book.getUrl();
            if (url == null || states.containsKey(url)) {
                continue;
            }
            states.put(url, savedState(downloadIndex, url));
        }
        return states;
    }

    /**
     * A chapter counts as downloaded when its download row is complete or the audio-list row records a local
     * copy; nothing is guessed from file names.
     */
    private int savedState(@NonNull DownloadIndex downloadIndex, @NonNull String bookUrl) {
        List<BooksAudioEntity> rows = mAudioListDBModel.getRows(bookUrl);
        if (rows.isEmpty()) {
            return NOT_SAVED;
        }
        List<String> urls = new ArrayList<>(rows.size());
        for (BooksAudioEntity row : rows) {
            urls.add(row.urlAudio);
        }
        Map<String, Download> downloads = DownloadUtil.downloadsByChapterUrl(mContext, downloadIndex, bookUrl, urls);
        int downloaded = 0;
        for (BooksAudioEntity row : rows) {
            Download download = row.urlAudio == null ? null : downloads.get(row.urlAudio);
            if ((download != null && download.state == Download.STATE_COMPLETED)
                    || DownloadStorageHelper.localCopyOf(mContext, row) != null) {
                downloaded++;
            }
        }
        if (downloaded == 0) {
            return NOT_SAVED;
        }
        return downloaded >= rows.size() ? SAVED_ALL : SAVED;
    }

    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
    }
}
