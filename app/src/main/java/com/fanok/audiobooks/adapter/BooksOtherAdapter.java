package com.fanok.audiobooks.adapter;

import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
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
import com.fanok.audiobooks.policy.DefaultCover;
import com.fanok.audiobooks.policy.ParentalControl;
import com.squareup.picasso.Picasso;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class BooksOtherAdapter extends RecyclerView.Adapter<BooksOtherAdapter.ViewHolder> {

    private ArrayList<BookPOJO> mData;

    public BooksOtherAdapter() {
        mData = new ArrayList<>();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(@NonNull ArrayList<BookPOJO> newData) {
        mData = new ArrayList<>(newData);
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.slider_item,
                viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        BookPOJO book = mData.get(i);
        Picasso.get().cancelRequest(viewHolder.mImageView);
        Drawable mask = ParentalControl.coverMask(viewHolder.mImageView.getContext(), book);
        if (mask != null) {
            viewHolder.mImageView.setImageDrawable(mask);
        } else if (DefaultCover.replaces(book.getPhoto())) {
            viewHolder.mImageView.setImageDrawable(
                    DefaultCover.drawable(viewHolder.mImageView.getContext(), book.getUrl()));
        } else if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
            Picasso.get().load(book.getPhoto())
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(viewHolder.mImageView);
        } else {
            viewHolder.mImageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        viewHolder.mTitle.setText(book.getName());
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    private void open(@NonNull Context context, int position) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        String url = mData.get(position).getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, LoadBook.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView mImageView;

        private final TextView mTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mImageView = itemView.findViewById(R.id.imageView);
            mTitle = itemView.findViewById(R.id.text);
            View.OnClickListener click = view -> open(view.getContext(), getBindingAdapterPosition());
            View.OnKeyListener key = (view, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KEYCODE_DPAD_CENTER
                        || keyCode == KEYCODE_ENTER || keyCode == KEYCODE_NUMPAD_ENTER)) {
                    open(view.getContext(), getBindingAdapterPosition());
                    return true;
                }
                return false;
            };
            mImageView.setOnClickListener(click);
            mTitle.setOnClickListener(click);
            mImageView.setOnKeyListener(key);
            mTitle.setOnKeyListener(key);
        }
    }
}
