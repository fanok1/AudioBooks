package com.fanok.audiobooks.adapter;

import static java.lang.Integer.MAX_VALUE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.adapter.ComentsListAddapter.OnListItemSelectedInterface;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.squareup.picasso.Picasso;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class ComentsListBazaKnigAddapter extends RecyclerView.Adapter<ComentsListBazaKnigAddapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final CircleImageView mImageView;

        private final TextView mName;

        private final TextView mQuoteName;

        private final LinearLayout mQuoteParent;

        private final TextView mQuoteText;

        private final TextView mReadMore;

        private final TextView mText;

        private final TextView mTime;

        private boolean showMore;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.imageView);
            mName = itemView.findViewById(R.id.name);
            mTime = itemView.findViewById(R.id.time);
            mText = itemView.findViewById(R.id.text);
            mReadMore = itemView.findViewById(R.id.readMore);
            mQuoteText = itemView.findViewById(R.id.quoteText);
            mQuoteName = itemView.findViewById(R.id.quoteName);
            mQuoteParent = itemView.findViewById(R.id.quoteParent);
        }

        void bind(ComentsPOJO comentsPOJO) {

            if (!comentsPOJO.getImage().isEmpty()) {
                Picasso.get().load(comentsPOJO.getImage()).into(mImageView);
            }
            mName.setText(comentsPOJO.getName());
            mTime.setText(comentsPOJO.getDate());
            String quoteName = comentsPOJO.getQuoteName();
            String quoteText = comentsPOJO.getQuoteText();
            if (quoteName.isEmpty() && quoteText.isEmpty()) {
                mQuoteParent.setVisibility(View.GONE);
            } else {
                mQuoteParent.setVisibility(View.VISIBLE);
                if (quoteName.isEmpty()) {
                    mQuoteName.setVisibility(View.GONE);
                } else {
                    mQuoteName.setVisibility(View.VISIBLE);
                    mQuoteName.setText(quoteName);
                }

                if (quoteText.isEmpty()) {
                    mQuoteText.setVisibility(View.GONE);
                } else {
                    mQuoteText.setVisibility(View.VISIBLE);
                    mQuoteText.setText(quoteText);
                }
            }

            mQuoteParent.setOnClickListener(view -> {
                if (mListener != null) {
                    mListener.onItemSelected(view, getBindingAdapterPosition());
                }
            });

            mText.setMaxLines(MAX_VALUE);
            mText.setText(comentsPOJO.getText());
            if (mText.getLineCount() <= MAX_LINES) {
                mReadMore.setVisibility(View.GONE);
            } else {
                mReadMore.setVisibility(View.VISIBLE);
            }
            mText.setMaxLines(MAX_LINES);
            showMore = false;

            mReadMore.setOnClickListener(view -> {
                if (!showMore) {
                    mText.setMaxLines(MAX_VALUE);
                    mReadMore.setText(R.string.show_less);
                } else {
                    mText.setMaxLines(MAX_LINES);
                    mReadMore.setText(R.string.show_more);
                }
                showMore = !showMore;
            });
        }
    }

    private static final int MAX_LINES = 5;

    private final Context mContext;

    private OnListItemSelectedInterface mListener;

    private ArrayList<ComentsPOJO> mModel;

    public ComentsListBazaKnigAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
    }

    public void clearItem() {
        int oldSize = mModel.size();
        if (oldSize > 0) {
            mModel.clear();
            notifyItemRangeRemoved(0, oldSize);
        }
    }


    public ComentsPOJO getItem(int position) {
        return mModel.get(position);
    }

    @Override
    public int getItemCount() {
        return mModel.size();
    }

    public int getParentQuoteId(int currentPosition) {
        String quote = mModel.get(currentPosition).getQuoteText();
        for (int i = 0; i < mModel.size(); i++) {
            ComentsPOJO coment = mModel.get(i);
            if (quote.equals(coment.getText())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i));
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.coments_recyler_item_baza_knig, viewGroup, false);
        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<ComentsPOJO> newModel) {
        mModel = newModel;
        notifyDataSetChanged();
    }

    public void setListener(OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
