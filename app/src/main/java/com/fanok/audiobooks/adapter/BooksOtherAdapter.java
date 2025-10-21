package com.fanok.audiobooks.adapter;

import static android.content.Context.UI_MODE_SERVICE;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.squareup.picasso.Picasso;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BooksOtherAdapter extends RecyclerView.Adapter<BooksOtherAdapter.ViewHolder> {
    private static final String TAG = "BooksOtherAdapter";

    private ArrayList<BookPOJO> mData;

    public BooksOtherAdapter() {
        mData = new ArrayList<>();
    }

    public void setData(@NonNull ArrayList<BookPOJO> data) {
        mData = data;
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        Log.d(TAG, "onCreateViewHolder: called");

        View view;
        UiModeManager uiModeManager = (UiModeManager) viewGroup.getContext().getSystemService(
                UI_MODE_SERVICE);
        if (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(
                    R.layout.slider_item_television,
                    viewGroup, false);
        } else {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.slider_item,
                    viewGroup, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        Log.d(TAG, "onBindViewHolder: called");

        if(App.useProxy&&mData.get(i).getPhoto().contains(Url.SERVER_BAZA_KNIG)){

            final Bitmap[] bmp = {null};
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {

                try {
                    URL url = new URL(mData.get(i).getPhoto());
                    Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress(Consts.PROXY_HOST, Consts.PROXY_PORT));
                    bmp[0] = BitmapFactory.decodeStream(url.openConnection(proxy).getInputStream());
                } catch (IOException ignored) {
                }
                handler.post(() -> {
                    if(bmp[0] !=null) {
                        viewHolder.mImageView.setImageBitmap(bmp[0]);
                    }
                });
            });


        }else {
            Picasso.get().load(mData.get(i).getPhoto()).into(viewHolder.mImageView);
        }

        viewHolder.mTitle.setText(mData.get(i).getName());
        viewHolder.mImageView.setOnClickListener(
                view -> myOnClick(view.getContext(), viewHolder.getAdapterPosition()));
        viewHolder.mTitle.setOnClickListener(
                view -> myOnClick(view.getContext(), viewHolder.getAdapterPosition()));
        viewHolder.mTitle.setOnKeyListener((view, i12, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getKeyCode() == KEYCODE_DPAD_CENTER || event.getKeyCode() == KEYCODE_ENTER
                        || event.getKeyCode() == KEYCODE_NUMPAD_ENTER) {
                    myOnClick(view.getContext(), viewHolder.getAdapterPosition());
                    return true;
                }
            }
            return false;
        });
        viewHolder.mImageView.setOnKeyListener((view, i1, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getKeyCode() == KEYCODE_DPAD_CENTER || event.getKeyCode() == KEYCODE_ENTER
                        || event.getKeyCode() == KEYCODE_NUMPAD_ENTER) {
                    myOnClick(view.getContext(), viewHolder.getAdapterPosition());
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    private void myOnClick(@NonNull Context context, int i) {
        Intent intent = new Intent(context, LoadBook.class);
        intent.putExtra("url", mData.get(i).getUrl());
        context.startActivity(intent);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView mImageView;

        private final TextView mTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.text);


        }
    }
}