package com.fanok.audiobooks.android_equalizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.R;

/**
 * A knob of {@code 0..max} steps on a 270° arc, 0 being "off": the knob turns grey and reads "Выкл". A finger on the
 * knob sets the step under it, by its angle from the centre; past either end of the arc (the gap at the bottom) the
 * knob holds the end it is nearest to, so the minimum and the maximum are reached by overshooting them. Lifting the
 * finger changes nothing. A tap without a drag (or a click from TalkBack) steps up, from the top back to off.
 *
 * Created by Harjot on 23-May-16.
 */
public class AnalogController extends View {

    private static final int OFF_COLOR = Color.parseColor("#555555");
    /** The arc on a circle of 24 positions, from the bottom clockwise: position 3 (bottom left) to 21 (bottom right). */
    private static final float ARC_START = 3, ARC_LENGTH = 18;

    float midx, midy;
    Paint textPaint, circlePaint, circlePaint2, linePaint;
    private int max = 19;
    private int value;
    private float downX, downY;
    private boolean dragged;
    /** Whether this touch has been on the arc: then the gap holds the end it left by, else the side it is on. */
    private boolean touchedArc;
    private int touchSlop;

    onProgressChangedListener mListener;

    String label;
    private String offText;

    public AnalogController(Context context) {
        super(context);
        init();
    }

    public AnalogController(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnalogController(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnProgressChangedListener(onProgressChangedListener listener) {
        mListener = listener;
    }

    void init() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(33);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.parseColor("#222222"));
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint2.setColor(EqualizerFragment.themeColor);
        circlePaint2.setStyle(Paint.Style.FILL);
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(EqualizerFragment.themeColor);
        linePaint.setStrokeWidth(7);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        label = "Label";
        offText = getContext().getString(R.string.equalizer_off);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private float radius() {
        return Math.min(midx, midy) * (14.5f / 16);
    }

    /** The point of {@code position} (0..24 around the circle, 0 at the bottom, clockwise) at {@code r}. */
    private float x(float position, float r) {
        return midx + (float) (r * Math.sin(2 * Math.PI * (1.0 - position / 24)));
    }

    private float y(float position, float r) {
        return midy + (float) (r * Math.cos(2 * Math.PI * (1.0 - position / 24)));
    }

    private float positionOf(int step) {
        return ARC_START + ARC_LENGTH * step / max;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        midx = (float) getWidth() / 2;
        midy = (float) getHeight() / 2;
        float radius = radius();
        boolean off = value == 0 || !isEnabled();

        for (int i = 0; i <= max; i++) {
            float position = positionOf(i);
            circlePaint.setColor(Color.parseColor("#111111"));
            canvas.drawCircle(x(position, radius), y(position, radius), radius / 15,
                    !off && i <= value ? circlePaint2 : circlePaint);
        }

        circlePaint.setColor(Color.parseColor("#222222"));
        canvas.drawCircle(midx, midy, radius * (13f / 15), circlePaint);
        circlePaint.setColor(Color.parseColor("#000000"));
        canvas.drawCircle(midx, midy, radius * (11f / 15), circlePaint);

        int textColor = textPaint.getColor();
        if (off) {
            textPaint.setColor(OFF_COLOR);
        }
        canvas.drawText(label, midx, midy + radius * 1.1f, textPaint);
        canvas.drawText(value == 0 ? offText : String.valueOf(value), midx,
                midy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint);
        textPaint.setColor(textColor);

        float position = positionOf(value);
        int lineColor = linePaint.getColor();
        if (off) {
            linePaint.setColor(OFF_COLOR);
        }
        canvas.drawLine(x(position, radius * 0.45f), y(position, radius * 0.45f),
                x(position, radius * 0.65f), y(position, radius * 0.65f), linePaint);
        linePaint.setColor(lineColor);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        change(value < max ? value + 1 : 0);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragged = false;
                touchedArc = false;
                // a scrolling parent must not take a turn of the knob for a scroll
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragged && Math.hypot(e.getX() - downX, e.getY() - downY) < touchSlop) {
                    return true;
                }
                dragged = true;
                follow(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragged) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    /** The step under the finger; near the centre the angle jumps, so the knob keeps its step there. */
    private void follow(float x, float y) {
        float dx = x - midx;
        float dy = y - midy;
        if (Math.hypot(dx, dy) < radius() * 0.3f) {
            return;
        }
        // 0..24 around the circle like the drawing: 0 at the bottom, clockwise
        double degrees = Math.toDegrees(Math.atan2(dy, dx)) - 90;
        float along = (float) (((degrees % 360) + 360) % 360 / 15) - ARC_START;
        int step;
        if (along >= 0 && along <= ARC_LENGTH) {
            touchedArc = true;
            step = Math.round(along / ARC_LENGTH * max);
        } else if (touchedArc) {
            step = value * 2 > max ? max : 0; // overshot an end into the gap below the arc: that end holds
        } else {
            step = along > ARC_LENGTH ? max : 0; // the gap's right half is past the maximum, its left half before 0
        }
        change(step);
    }

    private void change(int step) {
        if (step == value) {
            return;
        }
        value = step;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (mListener != null) {
            mListener.onProgressChanged(value);
        }
        invalidate();
    }

    /** The steps above "off" (0). */
    public void setMax(int max) {
        this.max = Math.max(1, max);
        value = Math.min(value, this.max);
        invalidate();
    }

    public int getProgress() {
        return value;
    }

    /** Shows {@code progress} (clamped to 0..max) without telling the listener. */
    public void setProgress(int progress) {
        value = Math.max(0, Math.min(max, progress));
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        invalidate();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String txt) {
        label = txt;
    }

    public interface onProgressChangedListener {
        void onProgressChanged(int progress);
    }
}
