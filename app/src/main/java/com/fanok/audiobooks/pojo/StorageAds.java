package com.fanok.audiobooks.pojo;

public class StorageAds {

    private static boolean disable = true;

    public StorageAds(boolean disable) {
        StorageAds.disable = disable;
    }

    public static void setDisableAds(boolean b) {
        disable = b;
    }

    public static boolean idDisableAds() {
        //return disable;
        return true;
    }

}
