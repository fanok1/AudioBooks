package com.fanok.audiobooks.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.search.Genre;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The allow-list editor: the app's genres in alphabetical order, one checkbox each, the same for every site. */
public class ParentalControlAddapter extends RecyclerView.Adapter<ParentalControlAddapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final CheckedTextView mCheckedTextView;

        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mCheckedTextView = itemView.findViewById(R.id.checkedTextView);
            mCheckedTextView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                mCheckedTextView.toggle();
                ParentalControl.setGenreAllowed(mStore, mGenres.get(position), mCheckedTextView.isChecked());
            });
        }

        void bind(int position) {
            Genre genre = mGenres.get(position);
            mCheckedTextView.setText(genre.title);
            mCheckedTextView.setChecked(ParentalControl.isGenreAllowed(mStore, genre));
            mCheckedTextView.setEnabled(enabled);
        }
    }

    private final List<Genre> mGenres;

    private final ParentalControl.Store mStore;

    private boolean enabled = false;

    public ParentalControlAddapter(@NonNull Context context, @NonNull ParentalControl.Store store) {
        mStore = store;
        mGenres = new ArrayList<>(Arrays.asList(Genre.values()));
        Collator collator = Collator.getInstance(new Locale("ru"));
        Collections.sort(mGenres, (a, b) -> collator.compare(context.getString(a.title), context.getString(b.title)));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(i);
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.parental_control_recycler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @Override
    public int getItemCount() {
        return mGenres.size();
    }
}
