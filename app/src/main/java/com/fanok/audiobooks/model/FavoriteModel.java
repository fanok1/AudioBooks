package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.util.DownloadUtil.getDownloadManager;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;

public class FavoriteModel implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoriteModel {

    private final BooksDBModel mBooksDBModel;
    private final AudioListDBModel mAudioListDBModel;
    private final Context mContext;

    public FavoriteModel(Context context) {
        mBooksDBModel = new BooksDBModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mContext = context;
    }

    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(int table) {
        return Observable.create(observableEmitter -> {
            try {
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
                    if (mContext != null) {
                        DownloadIndex downloadIndex = getDownloadManager().getDownloadIndex();
                        for (int i = 0; i < books.size(); i++) {
                            BookPOJO bookPOJO = books.get(i);
                            boolean hasDownloads = false;
                            ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(bookPOJO.getUrl());

                            if (arrayList != null && !arrayList.isEmpty()) {
                                try {
                                    for (AudioListPOJO pojo : arrayList) {
                                        Download download = downloadIndex.getDownload(pojo.getCleanAudioUrl());
                                        if (download != null && download.state == Download.STATE_COMPLETED) {
                                            hasDownloads = true;
                                            break;
                                        }
                                    }
                                } catch (IOException e) {
                                    // ignore
                                }
                            }

                            if (!hasDownloads) {
                                books.remove(i);
                                i--;
                                deleteSavedBook(bookPOJO);
                            }
                        }
                    }
                }
                observableEmitter.onNext(books);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private void deleteSavedBook(final BookPOJO bookPOJO) {
        if(mBooksDBModel.inSaved(bookPOJO)){
            mBooksDBModel.removeSaved(bookPOJO);
        }
    }


    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
    }
}
