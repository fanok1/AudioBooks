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
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;

public class AnswerListAddapter extends RecyclerView.Adapter<AnswerListAddapter.MyHolder> {

    private static final int MAX_LINES = 5;
    private ArrayList<SubComentsPOJO> mModel;

    static class MyHolder extends RecyclerView.ViewHolder {

        private final int height;

        private final View mBorderBottom;

        private final CircleImageView mImageView;

        private final TextView mName;

        private final TextView mParentName;

        private final TextView mReadMore;

        private final TextView mReting;

        private boolean showMore;

        private final TextView mText;

        private final TextView mTime;

        private final int width;

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
            if (!comentsPOJO.getReting().isEmpty()) {
                mReting.setText(comentsPOJO.getReting());
                mReting.setVisibility(View.VISIBLE);
            } else {
                mReting.setVisibility(View.GONE);
            }

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


            /*translation
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
                                    translator.translate(comentsPOJO.getName())
                                            .addOnSuccessListener(
                                                    translatedText -> mName.setText
                                                    (translatedText));

                                    translator.translate(comentsPOJO.getText())
                                            .addOnSuccessListener(
                                                    translatedText -> mText.setText
                                                    (translatedText));

                                    translator.translate(comentsPOJO.getDate())
                                            .addOnSuccessListener(
                                                    translatedText -> mTime.setText
                                                    (translatedText));

                                    translator.translate(comentsPOJO.getParentName())
                                            .addOnSuccessListener(
                                                    translatedText -> mParentName.setText
                                                    (translatedText));

                                });
            }*/

        }
    }

    private final Context mContext;


    public AnswerListAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    public SubComentsPOJO getItem(int position) {
        return mModel.get(position);
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
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.answer_recyler_item, viewGroup, false);
        return new MyHolder(view);
    }

    public void setItem(ArrayList<SubComentsPOJO> model) {
        mModel = model;
        notifyDataSetChanged();
    }
}
