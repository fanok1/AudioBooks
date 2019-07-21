package com.fanok.audiobooks.adapter;

import static java.lang.Integer.MAX_VALUE;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.squareup.picasso.Picasso;
import com.tolstykh.textviewrichdrawable.TextViewRichDrawable;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class ComentsListAddapter extends RecyclerView.Adapter<ComentsListAddapter.MyHolder> {

    private static final int MAX_LINES = 5;
    private ArrayList<ComentsPOJO> mModel;
    private OnListItemSelectedInterface mListener;
    private Context mContext;

    public ComentsListAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
    }

    public void setListener(OnListItemSelectedInterface listener) {
        mListener = listener;
    }

    public void setItem(ArrayList<ComentsPOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    public ComentsPOJO getItem(int position) {
        return mModel.get(position);
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.coments_recyler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i));
    }

    @Override
    public int getItemCount() {
        if (mModel == null) {
            return 0;
        } else {
            return mModel.size();
        }
    }


    public interface OnListItemSelectedInterface {
        void onItemSelected(View view, int position);
    }

    class MyHolder extends RecyclerView.ViewHolder {

        private CircleImageView mImageView;
        private TextView mName;
        private TextView mTime;
        private TextView mText;
        private TextView mReadMore;
        private TextViewRichDrawable mReting;
        private TextViewRichDrawable mComents;
        private TextView mShowAnswer;
        private boolean showMore;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.imageView);
            mName = itemView.findViewById(R.id.name);
            mTime = itemView.findViewById(R.id.time);
            mText = itemView.findViewById(R.id.text);
            mReadMore = itemView.findViewById(R.id.readMore);
            mReting = itemView.findViewById(R.id.reting);
            mComents = itemView.findViewById(R.id.coments);
            mShowAnswer = itemView.findViewById(R.id.showAnswer);

            mShowAnswer.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getAdapterPosition());
            });

            mComents.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getAdapterPosition());
            });


        }

        void bind(ComentsPOJO comentsPOJO) {
            if (!comentsPOJO.getImage().isEmpty()) {
                Picasso.get().load(comentsPOJO.getImage()).into(mImageView);
            }
            mName.setText(comentsPOJO.getName());
            mTime.setText(comentsPOJO.getDate());
            mReting.setText(comentsPOJO.getReting());
            int childCount = comentsPOJO.getChildComents().size();
            if (childCount == 0) {
                mComents.setVisibility(View.GONE);
                mShowAnswer.setVisibility(View.GONE);
            } else {
                mComents.setVisibility(View.VISIBLE);
                mShowAnswer.setVisibility(View.VISIBLE);
                mComents.setText(String.valueOf(childCount));
            }

            mText.setMaxLines(MAX_VALUE);
            mText.setText(comentsPOJO.getText());
            mText.post(() -> {
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
            });


        }
    }
}
