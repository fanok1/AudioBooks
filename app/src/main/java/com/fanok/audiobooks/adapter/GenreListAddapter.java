package com.fanok.audiobooks.adapter;


import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.GenrePOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class GenreListAddapter extends RecyclerView.Adapter<GenreListAddapter.MyHolder> {

    private RecyclerTushListner listner;
    private ArrayList<GenrePOJO> mModel;

    public void setClickListner(RecyclerTushListner listner) {
        this.listner = listner;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<GenrePOJO> newModel) {
        mModel = newModel == null ? new ArrayList<>() : new ArrayList<>(newModel);
        notifyDataSetChanged();
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

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.genre_recycler_item, viewGroup, false);

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


    public interface RecyclerTushListner {
        void onClickItem(View view, int position);
    }

    class MyHolder extends RecyclerView.ViewHolder {

        private final TextView mDescription;

        private final TextView mName;

        private final TextView mReting;

        MyHolder(@NonNull final View itemView) {
            super(itemView);

            mName = itemView.findViewById(R.id.name);
            mReting = itemView.findViewById(R.id.reting);
            mDescription = itemView.findViewById(R.id.desc);
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (listner != null && position != RecyclerView.NO_POSITION) {
                    listner.onClickItem(itemView, position);
                }
            });
        }

        void bind(GenrePOJO book) {
            if (book.getName() == null || book.getUrl() == null) {
                throw new NullPointerException();
            }
            mName.setText(book.getName());
            // a count the description already says is not repeated beside it
            String description = book.getDescription();
            if (book.getReting() != 0 && (description == null
                    || !description.replaceAll("\\D", "").equals(String.valueOf(book.getReting())))) {
                mReting.setText(String.valueOf(book.getReting()));
                mReting.setVisibility(View.VISIBLE);
            } else {
                mReting.setVisibility(View.GONE);
            }
            mDescription.setText(description);

        }
    }
}
