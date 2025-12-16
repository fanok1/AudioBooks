package com.fanok.audiobooks.adapter;

import static android.content.Context.MODE_PRIVATE;
import static com.fanok.audiobooks.activity.ParentalControlActivity.PARENTAL_CONTROL_PREFERENCES;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.pojo.ParentControlPOJO;
import java.util.ArrayList;

/** @noinspection ClassEscapesDefinedScope*/
public class ParentalControlAddapter extends
        RecyclerView.Adapter<ParentalControlAddapter.MyHolder> {

    class MyHolder extends RecyclerView.ViewHolder {

        private final CheckedTextView mCheckedTextView;

        private final TextView mTextView;

        MyHolder(@NonNull final View itemView) {
            super(itemView);
            mCheckedTextView = itemView.findViewById(R.id.checkedTextView);
            mTextView = itemView.findViewById(R.id.title);
        }

        void bind(int position) {
            mTextView.setEnabled(enabled);
            mTextView.setText(mModel.get(position).getSorceName());
            if (position == 0) {
                mTextView.setVisibility(View.VISIBLE);
            } else if (!mModel.get(position).getSorceName().equals(mModel.get(position - 1).getSorceName())) {
                mTextView.setVisibility(View.VISIBLE);
            } else {
                mTextView.setVisibility(View.GONE);
            }

            mCheckedTextView.setText(mModel.get(position).getName());
            mCheckedTextView.setChecked(mSharedPreferences.getBoolean(mModel.get(position).getUrl(), false));
            mCheckedTextView.setEnabled(enabled);
            mCheckedTextView.setOnClickListener(view -> {
                mCheckedTextView.toggle();
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putBoolean(mModel.get(position).getUrl(), mCheckedTextView.isChecked());
                editor.apply();
            });
        }
    }

    private ArrayList<ParentControlPOJO> mModel;

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
        notifyItemRangeChanged(0, getItemCount());
    }

    public ParentControlPOJO getItem(int position) {
        return mModel.get(position);
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

    @Override
    public void onBindViewHolder(@NonNull MyHolder myHolder, int i) {
        myHolder.bind(i);
    }


    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(
                R.layout.parental_control_recycler_item, viewGroup, false);
        return new MyHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItem(ArrayList<ParentControlPOJO> newModel) {
        mModel = newModel;
        notifyDataSetChanged();
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
