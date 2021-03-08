package com.fanok.audiobooks.adapter;

import static android.content.Context.MODE_PRIVATE;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public class ParentalControlAddapter extends
        RecyclerView.Adapter<ParentalControlAddapter.MyHolder> {

    private ArrayList<String> mModel;

    class MyHolder extends RecyclerView.ViewHolder {

        private final CheckedTextView mCheckedTextView;

        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mCheckedTextView = itemView.findViewById(R.id.checkedTextView);
        }

        void bind(@NotNull String string) {
            mCheckedTextView.setText(string);
            mCheckedTextView.setChecked(mSharedPreferences.getBoolean(string, false));
            mCheckedTextView.setEnabled(enabled);
            mCheckedTextView.setOnClickListener(view -> {
                mCheckedTextView.toggle();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putBoolean(string, mCheckedTextView.isChecked());
                editor.apply();
            });
        }
    }

    private final Context mContext;

    private boolean enabled = false;

    public ParentalControlAddapter(Context context) {
        mModel = new ArrayList<>();
        mContext = context;
        mSharedPreferences = context.getSharedPreferences(PARENTAL_CONTROL_PREFERENCES,
                MODE_PRIVATE);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyDataSetChanged();
    }

    public void setItem(ArrayList<String> model) {
        if (mModel != model) {
            mModel = model;
        }
        notifyDataSetChanged();
    }

    public void clearItem() {
        mModel.clear();
        notifyDataSetChanged();
    }

    public String getItem(int position) {
        return mModel.get(position);
    }


    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.parental_control_recycler_item, viewGroup, false);
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

    private final SharedPreferences mSharedPreferences;
}
