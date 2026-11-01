package com.fanok.audiobooks.service;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/**
 * The left/right balance of playback, played by the audio sink ({@link EndAwareRenderersFactory}): audiofx has no
 * balance effect. Mono is played as stereo so that it can lean to a side too. The balance is the app's one
 * ({@link #setBalance}), read for every buffer: a change is heard at once, spread over one buffer so it does not click.
 * The sink hands it 16-bit PCM (it converts other PCM first while float output is off, as it is here); anything else
 * and more than two channels pass untouched.
 */
@OptIn(markerClass = UnstableApi.class)
public final class BalanceAudioProcessor extends BaseAudioProcessor {

    /** The balance runs from all left ({@code -MAX}) through the centre (0) to all right ({@code MAX}). */
    public static final int MAX = 100;

    /** Gains of the left and right channel, replaced as a pair so a buffer never gets halves of two balances. */
    private static volatile float[] channelGains = {1f, 1f};

    /** The gains the last buffer ended with; playback thread. */
    private float left = 1f;
    private float right = 1f;

    /** Sets the balance, clamped to {@code ±MAX}; any thread. */
    public static void setBalance(int balance) {
        channelGains = gains(balance);
    }

    /**
     * The left and right gain for {@code balance}: the side it leans away from is turned down linearly, as Android's
     * own audio balance does, the other stays full. The centre is exactly 1 and 1: the samples play as they are.
     */
    static float[] gains(int balance) {
        int b = Math.max(-MAX, Math.min(MAX, balance));
        return new float[]{b > 0 ? 1f - (float) b / MAX : 1f, b < 0 ? 1f + (float) b / MAX : 1f};
    }

    @NonNull
    @Override
    protected AudioFormat onConfigure(@NonNull AudioFormat inputAudioFormat) {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                || inputAudioFormat.channelCount < 1 || inputAudioFormat.channelCount > 2) {
            return AudioFormat.NOT_SET;
        }
        return new AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT);
    }

    @Override
    public void queueInput(@NonNull ByteBuffer inputBuffer) {
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int frameSize = inputAudioFormat.bytesPerFrame;
        int frames = (limit - position) / frameSize;
        if (frames == 0) {
            return;
        }
        ByteBuffer buffer = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame);
        float[] target = channelGains;
        if (frameSize == 4 && left == 1f && right == 1f && target[0] == 1f && target[1] == 1f) {
            buffer.put(inputBuffer);
        } else {
            float leftStep = (target[0] - left) / frames;
            float rightStep = (target[1] - right) / frames;
            for (int i = position, end = position + frames * frameSize; i < end; i += frameSize) {
                left += leftStep;
                right += rightStep;
                short l = inputBuffer.getShort(i);
                short r = frameSize == 2 ? l : inputBuffer.getShort(i + 2);
                buffer.putShort(scale(l, left)).putShort(scale(r, right));
            }
            inputBuffer.position(limit);
        }
        left = target[0];
        right = target[1];
        buffer.flip();
    }

    @Override
    protected void onFlush(@NonNull StreamMetadata streamMetadata) {
        //a new stream starts at the balance, no ramp
        float[] target = channelGains;
        left = target[0];
        right = target[1];
    }

    private static short scale(short sample, float gain) {
        //saturated: a ramp's float steps may end a hair above 1, and 32768 would wrap to -32768 (a click)
        return gain == 1f ? sample
                : (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * gain)));
    }
}
