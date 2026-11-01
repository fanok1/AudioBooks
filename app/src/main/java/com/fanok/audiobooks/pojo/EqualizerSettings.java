package com.fanok.audiobooks.pojo;

public class EqualizerSettings {
    public int[] seekbarpos = new int[5];
    public int presetPos;
    public short reverbPreset;
    public short bassStrength;
    public boolean equalizerEnabled;
    /** Absent in settings saved before it existed: 0, the centre. */
    public int balance;
}
