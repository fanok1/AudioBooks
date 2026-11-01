package com.fanok.audiobooks.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;

public class BalanceAudioProcessorTest {

    private static final short[] SAMPLES = {0, 1, -1, 12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE, 7};

    @After
    public void centre() {
        BalanceAudioProcessor.setBalance(0);
    }

    @Test
    public void theCentrePlaysTheSamplesAsTheyAre() throws Exception {
        assertArrayEquals(SAMPLES, play(processor(2), SAMPLES));
        short[] mono = play(processor(1), SAMPLES);
        for (int i = 0; i < SAMPLES.length; i++) {
            assertEquals(SAMPLES[i], mono[2 * i]);
            assertEquals(SAMPLES[i], mono[2 * i + 1]);
        }
    }

    @Test
    public void monoPlaysAsStereoAndLeansToASide() throws Exception {
        BalanceAudioProcessor.setBalance(-100);
        short[] played = play(processor(1), SAMPLES);
        assertEquals(2 * SAMPLES.length, played.length);
        for (int i = 0; i < SAMPLES.length; i++) {
            assertEquals(SAMPLES[i], played[2 * i]);
            assertEquals(0, played[2 * i + 1]);
        }
    }

    @Test
    public void aChangeWhilePlayingIsSpreadOverOneBuffer() throws Exception {
        BalanceAudioProcessor processor = processor(2);
        short[] loud = new short[200];
        Arrays.fill(loud, (short) 10_000);
        BalanceAudioProcessor.setBalance(50);
        short[] ramp = play(processor, loud);
        assertTrue(ramp[0] < 10_000 && ramp[0] > 9_900);
        assertEquals(5_000, ramp[ramp.length - 2]);
        short[] after = play(processor, loud);
        for (int i = 0; i < after.length; i += 2) {
            //the left is turned down linearly, the right untouched
            assertEquals(5_000, after[i]);
            assertEquals(10_000, ramp[i + 1]);
            assertEquals(10_000, after[i + 1]);
        }
    }

    private static BalanceAudioProcessor processor(int channels) throws Exception {
        BalanceAudioProcessor processor = new BalanceAudioProcessor();
        assertEquals(2, processor.configure(new AudioFormat(44_100, channels, C.ENCODING_PCM_16BIT)).channelCount);
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT);
        return processor;
    }

    private static short[] play(BalanceAudioProcessor processor, short[] samples) {
        ByteBuffer input = ByteBuffer.allocateDirect(2 * samples.length).order(ByteOrder.nativeOrder());
        for (short sample : samples) {
            input.putShort(sample);
        }
        input.flip();
        processor.queueInput(input);
        assertFalse(input.hasRemaining());
        ByteBuffer output = processor.getOutput();
        short[] played = new short[output.remaining() / 2];
        for (int i = 0; i < played.length; i++) {
            played[i] = output.getShort();
        }
        return played;
    }
}
