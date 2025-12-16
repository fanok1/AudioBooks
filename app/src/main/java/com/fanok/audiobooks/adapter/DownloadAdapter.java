package com.fanok.audiobooks.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.exoplayer.offline.Download;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ItemDownloadBinding;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private ArrayList<DownloadItem> itemList;
    private SparseBooleanArray selectedItems = new SparseBooleanArray();

    public interface OnDownloadControlClickListener {
        void onRestartClick(DownloadItem item, int position);
        void onPauseClick(DownloadItem item, int position);
        void onResumeClick(DownloadItem item, int position);
    }

    public interface OnItemSelectionListener {
        void onSelectionChanged();
    }
    private OnItemSelectionListener selectionListener;

    public void setOnItemSelectionListener(OnItemSelectionListener listener) {
        this.selectionListener = listener;
    }

    private OnDownloadControlClickListener mListener;

    public DownloadAdapter(OnDownloadControlClickListener listener) {
        itemList = new ArrayList<>();
        mListener = listener;
    }

    public SparseBooleanArray getSelectedItems() {
        return selectedItems;
    }

    public int getSelectedItemsSize() {
        return selectedItems.size();
    }

    public DownloadItem getItem(int position) {
        return itemList.get(position);
    }


    public void setItemList(@NonNull ArrayList<DownloadItem> itemList) {
        this.itemList = new ArrayList<>(itemList);
        notifyDataSetChanged();
    }

    public void selectAll() {
        if (selectedItems.size() == itemList.size()) return;

        for (int i = 0; i < itemList.size(); i++) {
            selectedItems.put(i, true);
        }
        notifyDataSetChanged();

        if (selectionListener != null) {
           selectionListener.onSelectionChanged();
        }
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();

        if (selectionListener != null) {
            selectionListener.onSelectionChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemDownloadBinding binding = ItemDownloadBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = itemList.get(position);
        holder.bind(item, mListener);
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDownloadBinding binding;

        public ViewHolder(@NonNull ItemDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }


        @SuppressLint("UnsafeOptInUsageError")
        public void bind (DownloadItem item, OnDownloadControlClickListener listener) {

            Picasso.get()
                    .load(item.getFileIcon())
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.imageFileType);

            setIconForDetailTextView(binding.textBookName, R.drawable.ic_book_open_outline, 12);
            setIconForDetailTextView(binding.textAuthor, R.drawable.ic_autor, 12);
            setIconForDetailTextView(binding.textPerformer, R.drawable.ic_artist, 12);
            setIconForDetailTextView(binding.textChapterName, R.drawable.ic_bookmark_outline, 12);
            setIconForDetailTextView(binding.sourceName, R.drawable.ic_web, 12);

            if (item.getProgress()>0) {
                binding.progressDownload.setIndeterminate(false);
                binding.progressDownload.setProgress(item.getProgress());
            }else {
                binding.progressDownload.setIndeterminate(true);
            }



            // Название файла
            binding.textFileName.setText(item.getFileName());

            if (item.getBookName() == null || item.getBookName().isEmpty()) {
                binding.textBookName.setVisibility(View.GONE);
            } else {
                binding.textBookName.setVisibility(View.VISIBLE);
                binding.textBookName.setText(getStyledLabelAndValue("Книга:", item.getBookName()));
            }

            if (item.getAuthor() == null || item.getAuthor().isEmpty()) {
                binding.textAuthor.setVisibility(View.GONE);
            } else {
                binding.textAuthor.setVisibility(View.VISIBLE);
                binding.textAuthor.setText(getStyledLabelAndValue("Автор:", item.getAuthor()));
            }

            if (item.getReader() == null || item.getReader().isEmpty()) {
                binding.textPerformer.setVisibility(View.GONE);
            } else {
                binding.textPerformer.setVisibility(View.VISIBLE);
                binding.textPerformer.setText(getStyledLabelAndValue("Исполнитель:", item.getReader()));
            }

            if (item.getChapterName() == null || item.getChapterName().isEmpty()) {
                binding.textChapterName.setVisibility(View.GONE);
            } else {
                binding.textChapterName.setVisibility(View.VISIBLE);
                binding.textChapterName.setText(getStyledLabelAndValue("Трек:", item.getChapterName()));
            }

            if (item.getSource() == null || item.getSource().isEmpty()) {
                binding.sourceName.setVisibility(View.GONE);
            } else {
                binding.sourceName.setVisibility(View.VISIBLE);
                binding.sourceName.setText(getStyledLabelAndValue("Источник:", item.getSource()));
            }

            binding.buttonControlDownload.setOnClickListener(v -> {
                if (listener == null) return;

                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                int status = item.getStatus();

                switch (status) {
                    case Download.STATE_DOWNLOADING:
                    case Download.STATE_QUEUED:
                        listener.onPauseClick(item, position);
                        break;
                    case Download.STATE_STOPPED:
                        listener.onResumeClick(item, position);
                        break;
                    case Download.STATE_FAILED:
                        listener.onRestartClick(item, position);
                        break;
                    case Download.STATE_COMPLETED:
                    default:
                        break;
                }
            });


            itemView.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                boolean isVisible = binding.layoutDetails.getVisibility() == View.VISIBLE;
                binding.layoutDetails.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                item.setExpanded(!isVisible);
            });
            binding.layoutDetails.setVisibility(item.isExpanded() ? View.VISIBLE : View.GONE);


            binding.checkboxSelect.setOnClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                if (selectedItems.size() > 0) {
                    toggleSelection(adapterPosition);
                }
            });


            itemView.setOnLongClickListener(v -> {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return true;
                toggleSelection(adapterPosition);
                return true;
            });


            if (selectedItems.size() > 0){
                binding.checkboxSelect.setVisibility(View.VISIBLE);
            }else {
                binding.checkboxSelect.setVisibility(View.GONE);
            }
            binding.checkboxSelect.setChecked(selectedItems.get(getBindingAdapterPosition(), false));


            updateUiForStatus(item.getStatus());
        }


        private void toggleSelection(int position) {
            if (selectedItems.get(position, false)) {
                selectedItems.delete(position);
            } else {
                selectedItems.put(position, true);
            }
            notifyDataSetChanged();
            if (selectionListener != null) {
                selectionListener.onSelectionChanged();
            }
        }

        @SuppressLint("UnsafeOptInUsageError")
        private void updateUiForStatus(int status) {
            switch (status) {
                case Download.STATE_DOWNLOADING:
                case Download.STATE_QUEUED:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_pause, 24));
                    break;
                case Download.STATE_STOPPED:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_play, 24));
                    break;
                case Download.STATE_COMPLETED:
                    binding.progressDownload.setVisibility(View.INVISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_is_check, 24));
                    break;
                case Download.STATE_FAILED:
                    binding.progressDownload.setVisibility(View.INVISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_error, 24));
                    break;
                default:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_pause, 24));
                    break;
            }
        }


        @Nullable
        private Bitmap getBitMapWithFixedSize(int drawableResId, int targetSizeInDp) {
            Context context = itemView.getContext();
            Drawable originalDrawable = ContextCompat.getDrawable(context, drawableResId);

            if (originalDrawable == null) {
                return null;
            }
            float density = context.getResources().getDisplayMetrics().density;
            int targetSizeInPixels = (int) (targetSizeInDp * density);
            Bitmap bitmap = Bitmap.createBitmap(targetSizeInPixels, targetSizeInPixels, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            originalDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            originalDrawable.draw(canvas);
            return bitmap;
        }


        private void setIconForDetailTextView(TextView textView, int drawableResId, int sizeInDp) {
            Context context = itemView.getContext();
            Drawable originalDrawable = ContextCompat.getDrawable(context, drawableResId);

            if (originalDrawable == null) {
                return;
            }

            float density = context.getResources().getDisplayMetrics().density;
            int sizeInPixels = (int) (sizeInDp * density);
            originalDrawable.setBounds(0, 0, sizeInPixels, sizeInPixels);
            textView.setCompoundDrawables(originalDrawable, null, null, null);
        }

        private Spannable getStyledLabelAndValue(String label, String value) {
            SpannableStringBuilder builder = new SpannableStringBuilder();

            // Добавляем метку и применяем к ней жирный стиль
            builder.append(label);
            builder.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Добавляем пробел и значение
            builder.append("   ");
            builder.append(value);

            return builder;
        }



    }

    public void updateItems(@NonNull ArrayList<DownloadItem> newItems) {
        final ArrayList<DownloadItem> oldList = new ArrayList<>(this.itemList);
        for (DownloadItem newItem : newItems) {
            for (DownloadItem oldItem : oldList) {
                if (oldItem.getFileName().equals(newItem.getFileName())) {
                    newItem.setExpanded(oldItem.isExpanded());
                    break;
                }
            }
        }

        final DownloadDiffCallback diffCallback = new DownloadDiffCallback(oldList, newItems);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        // 3. Обновляем список и отправляем изменения в адаптер
        this.itemList.clear();
        this.itemList.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    private static class DownloadDiffCallback extends DiffUtil.Callback {

        private final List<DownloadItem> oldList;
        private final List<DownloadItem> newList;

        public DownloadDiffCallback(List<DownloadItem> oldList, List<DownloadItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getFileName().equals(newList.get(newItemPosition).getFileName());
        }

        // Проверяем, изменилось ли содержимое. Больше не меняем состояние!
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            DownloadItem oldItem = oldList.get(oldItemPosition);
            DownloadItem newItem = newList.get(newItemPosition);

            return oldItem.getStatus() == newItem.getStatus()
                    && oldItem.getProgress() == newItem.getProgress()
                    && oldItem.isExpanded() == newItem.isExpanded(); // Сравниваем, но не изменяем
        }
    }

}
