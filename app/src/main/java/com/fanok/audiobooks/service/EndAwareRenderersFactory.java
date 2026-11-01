package com.fanok.audiobooks.service;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import java.util.ArrayList;

/**
 * Media3's renderers, with an audio renderer that tells when it has played its stream out. Media3 ends a chapter only
 * when the clock reaches its declared duration: audio that ends sooner (a duration the file overstates, data cut short)
 * leaves the player playing silence with a moving clock, and a seek into that stretch has nothing to play either.
 * The service loads such a chapter again, or finishes it ({@link #endedEarly}, {@link #shouldRecover}).
 * Its audio sink plays the left/right balance ({@link BalanceAudioProcessor}).
 */
@OptIn(markerClass = UnstableApi.class)
final class EndAwareRenderersFactory extends DefaultRenderersFactory {

    /** Audio that ends this much before the declared duration, or more, ended early; less is the usual rounding. */
    static final long EARLY_END_MS = 2_000;
    /** A chapter loaded again after an early end starts this much before where it went silent. */
    static final long RECOVERY_REWIND_MS = 2_000;
    /** An early end this close to where the previous one was recovered is the same end. */
    static final long SAME_SPOT_MS = 10_000;

    /** Tags the source the renderer plays from then on (payload: an Integer); in order with the player's commands. */
    private static final int MSG_SET_SOURCE = Renderer.MSG_CUSTOM_BASE;
    private static final int NO_SOURCE = -1;

    @Nullable
    private Renderer audioRenderer;
    /** The tag of the last source given to the player; main thread. */
    private int sourceTag;
    /** The tag of the source whose audio has played out, else {@link #NO_SOURCE}; written on the playback thread. */
    private volatile int endedTag = NO_SOURCE;

    EndAwareRenderersFactory(@NonNull Context context) {
        super(context);
    }

    /**
     * Tags the source just given to {@code player}: from now on only its audio counts, never a late answer about the
     * previous one. Main thread, right after the source was set.
     */
    void onNewSource(@NonNull ExoPlayer player) {
        sourceTag++;
        if (audioRenderer != null) {
            player.createMessage(audioRenderer).setType(MSG_SET_SOURCE).setPayload(sourceTag).send();
        }
    }

    /**
     * Whether the audio of the source last given ({@link #onNewSource}) has played out; false again after a seek.
     * Media3 asks the renderer every few milliseconds while it plays, so the answer is fresh. Main thread.
     */
    boolean isAudioEnded() {
        return endedTag == sourceTag;
    }

    /** Whether audio that has played out at {@code positionMs} ended early for a chapter of {@code durationMs}. */
    static boolean endedEarly(boolean audioEnded, long positionMs, long durationMs) {
        return audioEnded && durationMs != C.TIME_UNSET && durationMs - positionMs >= EARLY_END_MS;
    }

    /**
     * Whether audio that ended early at {@code positionMs} gets a new source from about there, which brings the sound
     * back when the data was cut short: once per spot of a chapter. An end again at about the spot recovered before
     * ({@code recoveredAtMs}, {@link C#TIME_UNSET} for none) is where the chapter's audio really ends.
     */
    static boolean shouldRecover(long recoveredAtMs, long positionMs) {
        return recoveredAtMs == C.TIME_UNSET || Math.abs(positionMs - recoveredAtMs) > SAME_SPOT_MS;
    }

    @Override
    protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput,
            boolean enableAudioOutputPlaybackParameters) {
        //the default sink, with the balance ahead of Media3's own processors (speed)
        return new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                .setAudioProcessors(new AudioProcessor[]{new BalanceAudioProcessor()})
                .build();
    }

    @Override
    protected void buildAudioRenderers(@NonNull Context context, int extensionRendererMode,
            @NonNull MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
            @NonNull AudioSink audioSink, @NonNull Handler eventHandler,
            @NonNull AudioRendererEventListener eventListener, @NonNull ArrayList<Renderer> out) {
        //the default set without extensions (none are bundled): one MediaCodec audio renderer
        audioRenderer = new MediaCodecAudioRenderer(context, getCodecAdapterFactory(), mediaCodecSelector,
                enableDecoderFallback, eventHandler, eventListener, audioSink) {
            /** The tag of the source it plays; playback thread. */
            private int tag = NO_SOURCE;

            @Override
            public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
                if (messageType == MSG_SET_SOURCE && message instanceof Integer) {
                    tag = (Integer) message;
                    endedTag = NO_SOURCE;
                    return;
                }
                super.handleMessage(messageType, message);
            }

            @Override
            public boolean isEnded() {
                boolean ended = super.isEnded();
                endedTag = ended ? tag : NO_SOURCE;
                return ended;
            }

            @Override
            protected void onPositionReset(long positionUs, boolean joining, boolean sampleStreamIsResetToKeyFrame)
                    throws ExoPlaybackException {
                endedTag = NO_SOURCE;
                super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame);
            }

            @Override
            protected void onDisabled() {
                endedTag = NO_SOURCE;
                super.onDisabled();
            }
        };
        out.add(audioRenderer);
    }
}
