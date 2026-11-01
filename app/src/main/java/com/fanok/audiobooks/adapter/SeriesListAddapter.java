package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class SeriesListAddapter extends RecyclerView.Adapter<SeriesListAddapter.MyHolder> {

    private ArrayList<SeriesPOJO> mModel;
    class MyHolder extends RecyclerView.ViewHolder {


        private final TextView mLine;
        private final ImageView mImage;

        private final LinearLayout mLinearLayout;

        private final TextView mText;

        /** The row's own background (the theme's touch feedback), restored when a recycled row is not the book. */
        private final int mDefaultBackground;


        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mLine = itemView.findViewById(R.id.line);
            mImage = itemView.findViewById(R.id.image);
            mText = itemView.findViewById(R.id.text);
            mLinearLayout = itemView.findViewById(R.id.item);
            TypedValue value = new TypedValue();
            itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            mDefaultBackground = value.resourceId;

            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (mListener != null && position != RecyclerView.NO_POSITION) {
                    mListener.onItemSelected(view, position);
                }
            });


        }

        private boolean isPureWhite(Bitmap bitmap) {
            if (bitmap == null) return true;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 0 || height <= 0) return true;

            int sampled = 0;
            int nonWhite = 0;
            int stepX = Math.max(1, width / 6);
            int stepY = Math.max(1, height / 6);

            for (int x = 0; x < width; x += stepX) {
                for (int y = 0; y < height; y += stepY) {
                    sampled++;
                    int pixel = bitmap.getPixel(x, y);
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    if (r < 248 || g < 248 || b < 248) {
                        nonWhite++;
                    }
                }
            }
            return nonWhite == 0;
        }

        void bind(SeriesPOJO book) {
            boolean current = isCurrent(book.getUrl(), carentUrl);
            mLinearLayout.setBackgroundResource(current ? R.drawable.bg_series_current : mDefaultBackground);
            mLine.setTypeface(null, current ? Typeface.BOLD : Typeface.NORMAL);
            mText.setTypeface(null, current ? Typeface.BOLD : Typeface.NORMAL);
            itemView.setSelected(current);
            mLine.setText(book.getNumber());
            mText.setText(book.getName());
            if (mImage != null) {
                if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
                    Glide.with(itemView.getContext())
                            .asBitmap()
                            .load(com.fanok.audiobooks.net.Covers.glide(book.getPhoto()))
                            .listener(new RequestListener<Bitmap>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                    mImage.setVisibility(View.GONE);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                    if (resource != null && isPureWhite(resource)) {
                                        mImage.setVisibility(View.GONE);
                                        return true;
                                    } else {
                                        mImage.setVisibility(View.VISIBLE);
                                        return false;
                                    }
                                }
                            })
                            .into(mImage);
                } else {
                    mImage.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Whether a series entry is the book on screen: its link opens the same page, or it has no link at all (the
     * sites that list the open book without one: knigavuhe, izibuk, knigoblud).
     */
    static boolean isCurrent(@NonNull String entryUrl, @NonNull String bookUrl) {
        return entryUrl.isEmpty() || Url.samePage(entryUrl, bookUrl);
    }

    public interface OnListItemSelectedInterface {

        void onItemSelected(View view, int position);
    }

    private final String carentUrl;

    private OnListItemSelectedInterface mListener;

    public SeriesListAddapter(@NonNull String carentUrl) {
        this.carentUrl = carentUrl;
    }

    public void clearItem() {
        if (mModel != null) {
            int oldSize = mModel.size();
            if (oldSize > 0) {
                mModel.clear();
                notifyItemRangeRemoved(0, oldSize);
            }
        }
    }


    public SeriesPOJO getItem(int postion) {
        return mModel.get(postion);
    }

    @Override
    public int getItemCount() {
        if (mModel == null) {
            return 0;
        } else {
            return mModel.size();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i));
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.series_recyler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<SeriesPOJO> newModel) {
        mModel = newModel == null ? new ArrayList<>() : new ArrayList<>(newModel);
        notifyDataSetChanged();
    }


    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
