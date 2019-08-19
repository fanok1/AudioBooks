package com.fanok.audiobooks.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.BookPOJO;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class LoadBook extends AppCompatActivity {

    private BookPOJO mBookPOJO;
    private String mUrl;
    private Context mContext;

    @Override
    public void onBackPressed() {

    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.load_book_activity);
        mContext = this;
        Intent intent = getIntent();
        mUrl = intent.getStringExtra("url");
        if (mUrl == null) finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BookPOJO.getDescription(mUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<BookPOJO>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(BookPOJO bookPOJO) {
                        mBookPOJO = bookPOJO;
                    }

                    @Override
                    public void onError(Throwable e) {
                        finish();
                    }

                    @Override
                    public void onComplete() {
                        BookActivity.startNewActivity(mContext, mBookPOJO);
                    }
                });
    }
}
