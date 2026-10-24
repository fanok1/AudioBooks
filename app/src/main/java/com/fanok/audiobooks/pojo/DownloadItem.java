package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.download.DownloadStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of one book on the Downloads screen. {@link #getId()} (the book's {@code DownloadIds.bookKey})
 * is its stable identity; the state aggregates all of the book's chapters: {@code completed} have a completed
 * download or an exported copy, {@code selected} were chosen for download, {@code total} are listed.
 */
public final class DownloadItem {
    private final String id;
    private final String bookUrl;
    private final String fileIcon;
    private final String bookName;
    private final String author;
    private final String reader;
    private final String source;
    private final int status;
    private final int progress; // 0-100 over all chapters
    private final int completed;
    private final int selected;
    private final int total;
    private final List<String> downloadIds;
    private final List<Integer> exportedChapters;
    private final List<DownloadStatus.Part> parts;

    public DownloadItem(@NonNull String id, @NonNull String bookUrl, @Nullable String fileIcon, @Nullable String bookName,
                        @Nullable String author, @Nullable String reader, @Nullable String source, int status,
                        int progress, int completed, int selected, int total, @NonNull List<String> downloadIds,
                        @NonNull List<Integer> exportedChapters, @NonNull List<DownloadStatus.Part> parts) {
        this.id = id;
        this.bookUrl = bookUrl;
        this.fileIcon = fileIcon;
        this.bookName = bookName;
        this.author = author;
        this.reader = reader;
        this.source = source;
        this.status = status;
        this.progress = progress;
        this.completed = completed;
        this.selected = selected;
        this.total = total;
        this.downloadIds = Collections.unmodifiableList(new ArrayList<>(downloadIds));
        this.exportedChapters = Collections.unmodifiableList(new ArrayList<>(exportedChapters));
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
    }

    @NonNull
    public String getId() { return id; }
    @NonNull
    public String getBookUrl() { return bookUrl; }
    public String getFileIcon() { return fileIcon; }
    public String getBookName() { return bookName; }
    public String getAuthor() { return author; }
    public String getReader() { return reader; }
    public String getSource() { return source; }
    public int getStatus() { return status; }
    public int getProgress() { return progress; }
    public int getCompleted() { return completed; }
    public int getSelected() { return selected; }
    public int getTotal() { return total; }
    /** Ids of the book's downloads in the Media3 index, for pause, resume and restart. */
    @NonNull
    public List<String> getDownloadIds() { return downloadIds; }
    /** 1-based numbers of the chapters that have an exported copy in the download folder. */
    @NonNull
    public List<Integer> getExportedChapters() { return exportedChapters; }
    /** The book's transfers as the index had them, the input of {@code DownloadMonitor.statusOf}. */
    @NonNull
    public List<DownloadStatus.Part> getParts() { return parts; }

    /** Every listed chapter has a completed download or an exported copy. */
    public boolean isComplete() {
        return total > 0 && completed == total;
    }

    public boolean sameContents(@NonNull DownloadItem o) {
        return status == o.status && progress == o.progress && completed == o.completed && selected == o.selected
                && total == o.total && Objects.equals(bookUrl, o.bookUrl) && Objects.equals(fileIcon, o.fileIcon)
                && Objects.equals(bookName, o.bookName) && Objects.equals(author, o.author)
                && Objects.equals(reader, o.reader) && Objects.equals(source, o.source)
                && downloadIds.equals(o.downloadIds) && exportedChapters.equals(o.exportedChapters)
                && parts.equals(o.parts);
    }
}
