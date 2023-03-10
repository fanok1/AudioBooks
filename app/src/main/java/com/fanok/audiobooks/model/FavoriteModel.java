package com.fanok.audiobooks.model;

import android.content.Context;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.activity.PopupClearSaved;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.io.File;
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

                File[] folders = null;
                if(mContext!=null){
                    folders = mContext.getExternalFilesDirs(null);
                }
                if(folders!=null) {

                    ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.getAll();
                    for (int i=0; i<books.size(); i++) {
                        BookPOJO bookPOJO = books.get(i);
                        boolean temp = false;
                        for (File folder : folders) {
                            if (folder != null) {
                                String source = Consts.getSorceName(mContext, bookPOJO.getUrl());
                                String filePath = folder.getAbsolutePath() + "/" + source
                                        + "/" + bookPOJO.getAutor()
                                        + "/" + bookPOJO.getArtist()
                                        + "/" + bookPOJO.getName();
                                File dir = new File(filePath);
                                if (dir.exists() && dir.isDirectory()) {
                                    for (AudioListPOJO pojo : arrayList) {
                                        if(pojo.getBookUrl().equals(bookPOJO.getUrl())) {
                                            String url = pojo.getAudioUrl();
                                            File file = new File(dir,
                                                    url.substring(url.lastIndexOf("/") + 1));
                                            if (file.exists()) {
                                                temp = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!temp) {
                            if (table == Consts.TABLE_SAVED) {
                                books.remove(i);
                                i--;
                            }
                            deleteSavedBook(bookPOJO);
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
        File[] filesDirs = mContext.getExternalFilesDirs(null);
        for (final File filesDir : filesDirs) {
            if (filesDir != null) {
                File file = new File(filesDir.getAbsolutePath());
                PopupClearSaved.deleteEmtyFolder(file);
            }
        }
    }


    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
    }
}
