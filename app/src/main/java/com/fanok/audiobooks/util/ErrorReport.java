package com.fanok.audiobooks.util;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fanok.audiobooks.BuildConfig;

import java.io.PrintWriter;
import java.io.StringWriter;

/** The text of an error report the user can read and copy: versions, the book, the exception with all its causes. */
public final class ErrorReport {

    private ErrorReport() {
    }

    @NonNull
    public static String of(@Nullable Throwable error, @Nullable String bookUrl) {
        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        out.println("AudioBooks " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "), Android "
                + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        if (bookUrl != null) {
            out.println(bookUrl);
        }
        out.println();
        if (error == null) {
            out.println("no exception recorded");
        } else {
            error.printStackTrace(out);
        }
        out.flush();
        return text.toString();
    }
}
