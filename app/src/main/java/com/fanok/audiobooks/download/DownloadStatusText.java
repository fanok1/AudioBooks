package com.fanok.audiobooks.download;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.R;

import java.util.ArrayList;
import java.util.List;

/** The words of a {@link DownloadStatus}: one line for rows, headers and the notification. */
public final class DownloadStatusText {

    private static final String SEP = " · ";

    private DownloadStatusText() {
    }

    /**
     * E.g. "Скачивается · 12/40 глав · 245 МБ из ≈1,1 ГБ · 950 КБ/с · ~15 мин", "Ждём сеть · 12/40 глав · 245 МБ",
     * "Скачано полностью · 40 глав · 1,1 ГБ"; empty for {@link DownloadStatus#NONE}.
     */
    @NonNull
    public static String line(@NonNull Context context, @NonNull DownloadStatus s) {
        if (s.state == DownloadStatus.NONE) return "";
        Resources res = context.getResources();
        List<String> parts = new ArrayList<>(6);
        String label = stateLabel(res, s);
        if (label != null) parts.add(label);
        // no chapter counts (0) where chapters share one transfer and only bytes are known (the notification)
        if (s.state == DownloadStatus.COMPLETE) {
            if (s.chaptersTotal > 0) {
                parts.add(s.isCompleteBook()
                        ? res.getQuantityString(R.plurals.download_chapters_count, s.chaptersDone, s.chaptersDone)
                        : res.getQuantityString(R.plurals.download_chapters_of, s.chaptersTotal, s.chaptersDone, s.chaptersTotal));
            }
            if (s.bytesDone > 0) parts.add(bytes(res, s.bytesDone));
            return join(parts);
        }
        if (s.chaptersSelected > 0) {
            parts.add(res.getQuantityString(R.plurals.download_chapters_of, s.chaptersSelected, s.chaptersDone, s.chaptersSelected));
        }
        if (s.bytesTotal > 0) {
            parts.add(res.getString(R.string.download_bytes_of, bytes(res, s.bytesDone),
                    (s.bytesEstimated ? "≈" : "") + bytes(res, s.bytesTotal)));
        } else if (s.bytesDone > 0) {
            parts.add(bytes(res, s.bytesDone));
        }
        if (s.state == DownloadStatus.DOWNLOADING && s.speedBps > 0) {
            parts.add(res.getString(R.string.download_speed, bytes(res, s.speedBps)));
            if (s.etaSeconds != DownloadStatus.UNKNOWN) {
                parts.add(res.getString(R.string.download_eta,
                        DownloadStatus.formatDuration(s.etaSeconds, res.getStringArray(R.array.download_time_units))));
            }
        }
        if (s.failedParts > 0 && s.state != DownloadStatus.FAILED) {
            parts.add(res.getString(R.string.download_errors_count, s.failedParts));
        }
        return join(parts);
    }

    /** The state alone, e.g. for a menu item: "скачивается 12/40", "скачано", "ждём сеть"; null for NONE. */
    @Nullable
    public static String brief(@NonNull Context context, @NonNull DownloadStatus s) {
        if (s.state == DownloadStatus.NONE) return null;
        Resources res = context.getResources();
        if (s.state == DownloadStatus.COMPLETE) {
            return s.isCompleteBook() ? res.getString(R.string.download_status_complete_all)
                    : res.getQuantityString(R.plurals.download_chapters_of, s.chaptersTotal, s.chaptersDone, s.chaptersTotal);
        }
        return stateLabel(res, s) + " " + s.chaptersDone + "/" + s.chaptersSelected;
    }

    @Nullable
    private static String stateLabel(Resources res, DownloadStatus s) {
        switch (s.state) {
            case DownloadStatus.DOWNLOADING:
                return res.getString(R.string.download_status_downloading);
            case DownloadStatus.STALLED:
                return res.getString(R.string.download_status_stalled);
            case DownloadStatus.QUEUED:
                return res.getString(R.string.download_status_queued);
            case DownloadStatus.WAITING_NETWORK:
                return res.getString(R.string.download_status_waiting_network);
            case DownloadStatus.PAUSED:
                return res.getString(R.string.download_status_paused);
            case DownloadStatus.FAILED:
                return s.failure == null ? res.getString(R.string.download_status_failed)
                        : res.getString(R.string.download_status_failed_reason, s.failure);
            case DownloadStatus.COMPLETE:
                return res.getString(s.isCompleteBook() ? R.string.download_status_complete_all
                        : R.string.download_status_complete_part);
            default:
                return null;
        }
    }

    @NonNull
    public static String bytes(@NonNull Resources res, long bytes) {
        return DownloadStatus.formatBytes(bytes, res.getStringArray(R.array.download_byte_units));
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (b.length() > 0) b.append(SEP);
            b.append(p);
        }
        return b.toString();
    }
}
