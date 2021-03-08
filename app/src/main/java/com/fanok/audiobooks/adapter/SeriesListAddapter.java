package com.fanok.audiobooks.adapter;

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
                if (mListener != null) mListener.onItemSelected(view, getAdapterPosition());
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

            /*//translation
            String lang = Locale.getDefault().toLanguageTag();
            if(!lang.equals("ru")) {
                FirebaseTranslatorOptions options =
                        new FirebaseTranslatorOptions.Builder()
                                .setSourceLanguage(FirebaseTranslateLanguage.RU)
                                .setTargetLanguage(FirebaseTranslateLanguage
                                .languageForLanguageCode(lang))
                                .build();
                final FirebaseTranslator translator =
                        FirebaseNaturalLanguage.getInstance().getTranslator(options);

                FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions
                .Builder()
                        .requireWifi()
                        .build();
                translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener(
                                v -> {
                                    translator.translate(book.getName())
                                            .addOnSuccessListener(
                                                    translatedText -> mText.setText
                                                    (translatedText));
                                });
            }*/

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
        mModel.clear();
        notifyDataSetChanged();
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

    public void setItem(ArrayList<SeriesPOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }

    public void setListener(
            OnListItemSelectedInterface listener) {
        mListener = listener;
    }
}
