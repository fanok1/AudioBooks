package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.SeriesPOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class SeriesListAddapter extends RecyclerView.Adapter<SeriesListAddapter.MyHolder> {

    private ArrayList<SeriesPOJO> mModel;
    class MyHolder extends RecyclerView.ViewHolder {


        private final TextView mLine;

        private final LinearLayout mLinearLayout;

        private final TextView mText;


        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mLine = itemView.findViewById(R.id.line);
            mText = itemView.findViewById(R.id.text);
            mLinearLayout = itemView.findViewById(R.id.item);

            itemView.setOnClickListener(view -> {
                if (mListener != null) mListener.onItemSelected(view, getBindingAdapterPosition());
            });


        }

        void bind(SeriesPOJO book) {
            if (book.getUrl().equals(carentUrl)) {
                mLinearLayout.setBackgroundColor(
                        Consts.getAttributeColor(mLinearLayout.getContext(),
                                R.attr.mySelectableItemBackground));
            }
            mLine.setText(book.getNumber());
            mText.setText(book.getName());
        }
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
        mModel = newModel;
        notifyDataSetChanged();
    }


    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
