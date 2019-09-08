package com.fanok.audiobooks.adapter;

import static java.lang.Integer.MAX_VALUE;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class AnswerListAddapter extends RecyclerView.Adapter<AnswerListAddapter.MyHolder> {

    private static final int MAX_LINES = 5;
    private ArrayList<SubComentsPOJO> mModel;
    private Context mContext;

    public AnswerListAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
    }


    public void setItem(ArrayList<SubComentsPOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    public SubComentsPOJO getItem(int position) {
        return mModel.get(position);
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.answer_recyler_item, viewGroup, false);
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

    class MyHolder extends RecyclerView.ViewHolder {

        private CircleImageView mImageView;
        private TextView mName;
        private TextView mTime;
        private TextView mText;
        private TextView mReadMore;
        private TextView mReting;
        private TextView mParentName;
        private boolean showMore;
        private View mBorderBottom;
        private int height;
        private int width;

        MyHolder(@NonNull final View itemView) {
            super(itemView);


            mImageView = itemView.findViewById(R.id.imageView);
            mName = itemView.findViewById(R.id.name);
            mTime = itemView.findViewById(R.id.time);
            mText = itemView.findViewById(R.id.text);
            mReadMore = itemView.findViewById(R.id.readMore);
            mReting = itemView.findViewById(R.id.reting);
            mParentName = itemView.findViewById(R.id.parentName);
            mBorderBottom = itemView.findViewById(R.id.border);

            ViewGroup.LayoutParams layoutParams = mImageView.getLayoutParams();
            height = (int) (layoutParams.height * 1.33);
            width = (int) (layoutParams.width * 1.33);

        }

        void bind(SubComentsPOJO comentsPOJO) {

            if (getAdapterPosition() == 0) {


                mImageView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
                mParentName.setVisibility(View.GONE);
                mBorderBottom.setVisibility(View.VISIBLE);
            }

            if (!comentsPOJO.getImage().isEmpty()) {
                Picasso.get().load(comentsPOJO.getImage()).into(
                        mImageView);
            }
            mName.setText(comentsPOJO.getName());
            mTime.setText(comentsPOJO.getDate());
            mReting.setText(comentsPOJO.getReting());

            mText.setMaxLines(MAX_VALUE);
            mText.setText(comentsPOJO.getText());

            if (getAdapterPosition() != 0) {
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
            } else {
                mReadMore.setVisibility(View.GONE);
            }


            mParentName.setText(comentsPOJO.getParentName());


        }
    }
}
