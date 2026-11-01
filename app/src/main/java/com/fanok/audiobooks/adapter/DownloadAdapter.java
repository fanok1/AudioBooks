package com.fanok.audiobooks.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ItemDownloadBinding;
import com.fanok.audiobooks.download.DownloadMonitor;
import com.fanok.audiobooks.download.DownloadStatus;
import com.fanok.audiobooks.download.DownloadStatusText;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.policy.DefaultCover;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the Downloads screen. The adapter holds an immutable snapshot that is replaced as a whole; selection and
 * expansion are kept by the row's stable id, so a refresh never moves them to another book.
 */
@OptIn(markerClass = UnstableApi.class)
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    /** Change payload: only the live status of the row is redrawn. */
    private static final Object LIVE = new Object();

    private List<DownloadItem> itemList = Collections.emptyList();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Set<String> expandedIds = new HashSet<>();

    public interface OnDownloadControlClickListener {
        void onRestartClick(DownloadItem item);
        void onPauseClick(DownloadItem item);
        void onResumeClick(DownloadItem item);
        void onOpenClick(DownloadItem item);
    }

    public interface OnItemSelectionListener {
        void onSelectionChanged();
    }
    private OnItemSelectionListener selectionListener;

    public void setOnItemSelectionListener(OnItemSelectionListener listener) {
        this.selectionListener = listener;
    }

    private final OnDownloadControlClickListener mListener;

    public DownloadAdapter(OnDownloadControlClickListener listener) {
        mListener = listener;
    }

    /** The selected rows of the current snapshot, resolved now into an immutable list. */
    @NonNull
    public List<DownloadItem> getSelectedItems() {
        List<DownloadItem> selected = new ArrayList<>();
        for (DownloadItem item : itemList) {
            if (selectedIds.contains(item.getId())) selected.add(item);
        }
        return Collections.unmodifiableList(selected);
    }

    public int getSelectedItemsSize() {
        return selectedIds.size();
    }

    @Nullable
    public DownloadItem getItem(int position) {
        return position >= 0 && position < itemList.size() ? itemList.get(position) : null;
    }

    /** Redraws the row of {@code item} (e.g. after a cancelled swipe), wherever it is now. */
    public void restore(@NonNull DownloadItem item) {
        for (int i = 0; i < itemList.size(); i++) {
            if (itemList.get(i).getId().equals(item.getId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Shows a new snapshot; rows are matched by id, and selections of rows that are gone are dropped. */
    public void submit(@NonNull List<DownloadItem> snapshot) {
        final List<DownloadItem> oldList = itemList;
        final List<DownloadItem> newList = Collections.unmodifiableList(new ArrayList<>(snapshot));
        Set<String> ids = new HashSet<>();
        for (DownloadItem item : newList) ids.add(item.getId());
        boolean selectionChanged = selectedIds.retainAll(ids);
        expandedIds.retainAll(ids);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DownloadDiffCallback(oldList, newList));
        itemList = newList;
        diff.dispatchUpdatesTo(this);
        if (selectionChanged) {
            notifySelectionChanged();
        }
    }

    public void selectAll() {
        if (selectedIds.size() == itemList.size()) return;
        for (DownloadItem item : itemList) selectedIds.add(item.getId());
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(itemList.get(position), mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(LIVE)) {
            holder.bindStatus(itemList.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    /** The book's status now: the snapshot's transfers with the manager's live state of the ones in flight. */
    @NonNull
    private static DownloadStatus statusOf(@NonNull DownloadItem item) {
        return DownloadMonitor.get().statusOf(item.getParts(), item.getCompleted(), item.getSelected(), item.getTotal());
    }

    /** Redraws the status of the rows still in flight (a tick of the monitor), without touching the rest. */
    public void refreshLive() {
        for (int i = 0; i < itemList.size(); i++) {
            DownloadItem item = itemList.get(i);
            if (!item.getParts().isEmpty() && !item.isComplete()) notifyItemChanged(i, LIVE);
        }
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


        public void bind (DownloadItem item, OnDownloadControlClickListener listener) {

            String fileIcon = item.getFileIcon();
            if (fileIcon != null && !fileIcon.trim().isEmpty()) {
                try {
                    if (DefaultCover.replaces(fileIcon)) {
                        Picasso.get().cancelRequest(binding.imageFileType);
                        binding.imageFileType.setImageDrawable(
                                DefaultCover.drawable(binding.imageFileType.getContext(), item.getBookUrl()));
                    } else if (fileIcon.startsWith("content://")) {
                        Picasso.get()
                                .load(android.net.Uri.parse(fileIcon))
                                .error(android.R.drawable.ic_menu_gallery)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .into(binding.imageFileType);
                    } else if (fileIcon.startsWith("http://") || fileIcon.startsWith("https://")) {
                        Picasso.get()
                                .load(fileIcon)
                                .error(android.R.drawable.ic_menu_gallery)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .into(binding.imageFileType);
                    } else {
                        java.io.File imgFile = new java.io.File(fileIcon);
                        if (imgFile.exists()) {
                            Picasso.get()
                                    .load(imgFile)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .into(binding.imageFileType);
                        } else {
                            binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                } catch (Exception e) {
                    binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            setIconForDetailTextView(binding.textBookName, R.drawable.ic_book_open_outline, 12);
            setIconForDetailTextView(binding.textAuthor, R.drawable.ic_autor, 12);
            setIconForDetailTextView(binding.textPerformer, R.drawable.ic_artist, 12);
            setIconForDetailTextView(binding.textChapterName, R.drawable.ic_bookmark_outline, 12);
            setIconForDetailTextView(binding.textExported, R.drawable.ic_download, 12);
            setIconForDetailTextView(binding.sourceName, R.drawable.ic_web, 12);

            Context context = itemView.getContext();
            String title = item.getBookName() == null || item.getBookName().isEmpty() ? item.getBookUrl() : item.getBookName();
            binding.textFileName.setText(title);

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

            binding.textChapterName.setVisibility(View.VISIBLE);
            binding.textChapterName.setText(getStyledLabelAndValue(context.getString(R.string.download_chapters_label),
                    context.getString(R.string.download_chapters_value, item.getCompleted(), item.getTotal(), item.getSelected())));

            binding.textExported.setVisibility(View.VISIBLE);
            binding.textExported.setText(getStyledLabelAndValue(context.getString(R.string.download_exported_label),
                    item.getExportedChapters().isEmpty() ? context.getString(R.string.download_exported_none)
                            : TextUtils.join(", ", item.getExportedChapters())));

            if (item.getSource() == null || item.getSource().isEmpty()) {
                binding.sourceName.setVisibility(View.GONE);
            } else {
                binding.sourceName.setVisibility(View.VISIBLE);
                binding.sourceName.setText(getStyledLabelAndValue("Источник:", item.getSource()));
            }

            binding.buttonControlDownload.setOnClickListener(v -> {
                if (listener == null) return;
                switch (statusOf(item).state) {
                    case DownloadStatus.DOWNLOADING:
                    case DownloadStatus.STALLED:
                    case DownloadStatus.QUEUED:
                    case DownloadStatus.WAITING_NETWORK:
                        listener.onPauseClick(item);
                        break;
                    case DownloadStatus.PAUSED:
                        listener.onResumeClick(item);
                        break;
                    case DownloadStatus.FAILED:
                        listener.onRestartClick(item);
                        break;
                    case DownloadStatus.COMPLETE:
                        listener.onOpenClick(item);
                        break;
                    default:
                        break;
                }
            });


            final String id = item.getId();
            itemView.setOnClickListener(v -> {
                boolean isVisible = binding.layoutDetails.getVisibility() == View.VISIBLE;
                binding.layoutDetails.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                if (isVisible) expandedIds.remove(id); else expandedIds.add(id);
            });
            binding.layoutDetails.setVisibility(expandedIds.contains(id) ? View.VISIBLE : View.GONE);


            binding.checkboxSelect.setOnClickListener(v -> {
                if (!selectedIds.isEmpty()) {
                    toggleSelection(id);
                }
            });


            itemView.setOnLongClickListener(v -> {
                toggleSelection(id);
                return true;
            });


            binding.checkboxSelect.setVisibility(selectedIds.isEmpty() ? View.GONE : View.VISIBLE);
            binding.checkboxSelect.setChecked(selectedIds.contains(id));


            bindStatus(item);
        }


            private void toggleSelection(String id) {
            if (!selectedIds.remove(id)) {
                selectedIds.add(id);
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        /** The live part of the row: determinate bar, status line and the control button for the current state. */
        void bindStatus(DownloadItem item) {
            DownloadStatus status = statusOf(item);
            Context context = itemView.getContext();
            String line = DownloadStatusText.line(context, status);
            binding.textStatus.setText(line);
            binding.textStatus.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
            binding.progressDownload.setIndeterminate(false);
            binding.progressDownload.setProgress(status.percent());
            // A book is complete only when every chapter is; otherwise the bar shows how much of it is.
            boolean barHidden = status.state == DownloadStatus.NONE
                    || (status.state == DownloadStatus.COMPLETE && item.isComplete());
            binding.progressDownload.setVisibility(barHidden ? View.INVISIBLE : View.VISIBLE);
            int icon;
            switch (status.state) {
                case DownloadStatus.PAUSED:
                    icon = R.drawable.ic_play;
                    break;
                case DownloadStatus.COMPLETE:
                    icon = R.drawable.ic_is_check;
                    break;
                case DownloadStatus.FAILED:
                    icon = R.drawable.ic_error;
                    break;
                default:
                    icon = R.drawable.ic_pause;
                    break;
            }
            binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(icon, 24));
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

    private static class DownloadDiffCallback extends DiffUtil.Callback {

        private final List<DownloadItem> oldList;
        private final List<DownloadItem> newList;

        DownloadDiffCallback(List<DownloadItem> oldList, List<DownloadItem> newList) {
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
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).sameContents(newList.get(newItemPosition));
        }
    }

}
