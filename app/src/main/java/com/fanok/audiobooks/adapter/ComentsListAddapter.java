package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.squareup.picasso.Picasso;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** @noinspection ClassEscapesDefinedScope*/
public class ComentsListAddapter extends RecyclerView.Adapter<ComentsListAddapter.MyHolder> {

    private static final int MAX_LINES = 5;
    private ArrayList<ComentsPOJO> mModel;
    private OnListItemSelectedInterface mListener;
    /** Positions whose text is expanded; cleared with the data (UI-09). */
    private final Set<Integer> expanded = new HashSet<>();

    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mComents;

        private final CircleImageView mImageView;

        private final TextView mName;

        private final TextView mReadMore;

        private final TextView mRating;

        private final TextView mShowAnswer;

        private final TextView mText;

        private final TextView mTime;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mImageView = itemView.findViewById(R.id.imageView);
            mName = itemView.findViewById(R.id.name);
            mTime = itemView.findViewById(R.id.time);
            mText = itemView.findViewById(R.id.text);
            mReadMore = itemView.findViewById(R.id.readMore);
            mRating = itemView.findViewById(R.id.reting);
            mComents = itemView.findViewById(R.id.coments);
            mShowAnswer = itemView.findViewById(R.id.showAnswer);

            mShowAnswer.setOnClickListener(this::select);
            mComents.setOnClickListener(this::select);
            mReadMore.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                if (!expanded.remove(position)) {
                    expanded.add(position);
                }
                notifyItemChanged(position);
            });
        }

        private void select(View view) {
            int position = getBindingAdapterPosition();
            if (mListener != null && position != RecyclerView.NO_POSITION) {
                mListener.onItemSelected(view, position);
            }
        }

        void bind(ComentsPOJO comentsPOJO, boolean isExpanded) {
            Picasso.get().cancelRequest(mImageView);
            if (!comentsPOJO.getImage().isEmpty()) {
                Picasso.get().load(comentsPOJO.getImage()).into(mImageView);
            } else {
                mImageView.setImageDrawable(null);
            }
            mName.setText(comentsPOJO.getName());
            mTime.setText(comentsPOJO.getDate());
            if (!comentsPOJO.getReting().isEmpty()) {
                mRating.setText(comentsPOJO.getReting());
                mRating.setVisibility(View.VISIBLE);
            } else {
                mRating.setVisibility(View.GONE);
            }
            int childCount = comentsPOJO.getChildComents().size();
            if (childCount == 0) {
                mComents.setVisibility(View.GONE);
                mShowAnswer.setVisibility(View.GONE);
            } else {
                mComents.setVisibility(View.VISIBLE);
                mShowAnswer.setVisibility(View.VISIBLE);
                mComents.setText(String.valueOf(childCount));
            }

            ExpandableText.bind(mText, mReadMore, comentsPOJO.getText(), MAX_LINES, isExpanded);
        }
    }

    public interface OnListItemSelectedInterface {

        void onItemSelected(View view, int position);
    }

    private final Context mContext;

    public ComentsListAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
    }

    public void clearItem() {
        int oldSize = mModel.size();
        expanded.clear();
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

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(mModel.get(i), expanded.contains(i));
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.coments_recyler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<ComentsPOJO> newModel) {
        mModel = newModel == null ? new ArrayList<>() : new ArrayList<>(newModel);
        expanded.clear();
        notifyDataSetChanged();
    }

    public void setListener(OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
