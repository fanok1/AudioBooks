package com.fanok.audiobooks.adapter;

import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.R;

/**
 * A collapsible text with a "read more / show less" toggle. The toggle is shown only when the text really
 * overflows, measured after layout (not right after {@code setText}, where the line count is stale), and the
 * label always matches the state the caller keeps per item (UI-09).
 */
public final class ExpandableText {

    private ExpandableText() {
    }

    /**
     * Binds {@code content} into {@code text} collapsed to {@code maxLines} or expanded, and sizes the toggle once
     * the text is laid out. The caller flips its state on the toggle's click and binds again.
     */
    public static void bind(@NonNull TextView text, @NonNull TextView toggle, @Nullable CharSequence content,
                            int maxLines, boolean expanded) {
        text.setMaxLines(expanded ? Integer.MAX_VALUE : maxLines);
        text.setText(content == null ? "" : content);
        toggle.setText(expanded ? R.string.show_less : R.string.show_more);
        if (expanded) {
            toggle.setVisibility(View.VISIBLE);
            return;
        }
        toggle.setVisibility(View.GONE);
        text.post(() -> {
            if (text.getMaxLines() != maxLines) {
                return; // re-bound meanwhile
            }
            Layout layout = text.getLayout();
            boolean overflows = layout != null && (layout.getLineCount() > maxLines
                    || (layout.getLineCount() == maxLines && layout.getEllipsisCount(maxLines - 1) > 0));
            toggle.setVisibility(overflows ? View.VISIBLE : View.GONE);
        });
    }
}
