package com.fanok.audiobooks.pojo;

import androidx.media3.exoplayer.offline.Download;

public class DownloadItem {
    private String fileIcon;
    private String fileName;
    private String bookName;

    private String author;
    private String reader;
    private String chapterName;
    private String source;
    private int progress; // 0-100

    private int status;

    private boolean isExpanded;

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public DownloadItem(String fileIcon, String fileName, String bookName, String author, String reader, String chapterName, String source, int progress, int status) {
        this.fileIcon = fileIcon;
        this.fileName = fileName;
        this.bookName = bookName;
        this.author = author;
        this.reader = reader;
        this.chapterName = chapterName;
        this.source = source;
        this.progress = progress;
        this.status = status;
        this.isExpanded = false;
    }

    public DownloadItem(String fileName, int status) {
        this.fileName = fileName;
        this.status = status;
        isExpanded = false;
    }

    public String getFileIcon() {
        return fileIcon;
    }

    public void setFileIcon(String fileIcon) {
        this.fileIcon = fileIcon;
    }

    public String getFileName() { return fileName; }
    public String getBookName() { return bookName; }
    public String getChapterName() { return chapterName; }
    public int getProgress() { return progress; }
    public int getStatus() { return status; }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getReader() {
        return reader;
    }

    public void setReader(String reader) {
        this.reader = reader;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public void setChapterName(String chapterName) {
        this.chapterName = chapterName;
    }

    public void setProgress(int progress) { this.progress = progress; }
    public void setStatus(int status) { this.status = status; }

    public DownloadItem(DownloadItem other) {
        this.fileIcon = other.fileIcon;
        this.fileName = other.fileName;
        this.bookName = other.bookName;
        this.author = other.author;
        this.reader = other.reader;
        this.chapterName = other.chapterName;
        this.source = other.source;
        this.progress = other.progress;
        this.status = other.status;
        this.isExpanded = other.isExpanded;
    }

}