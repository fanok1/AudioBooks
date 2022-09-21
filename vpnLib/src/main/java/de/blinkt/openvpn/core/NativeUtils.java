/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.os.Build;
import java.security.InvalidKeyException;

public class NativeUtils {
    public final static int[] openSSLlengths = {
        16, 64, 256, 1024, 8 * 1024, 16 * 1024
    };

    public static native String[] getIfconfig() throws IllegalArgumentException;

    public static String getNativeAPI()
    {
        if (isRoboUnitTest())
            return "ROBO";
        else
            return getJNIAPI();
    }

    public static native double[] getOpenSSLSpeed(String algorithm, int testnum);

    public static native String getOpenVPN2GitVersion();

    public static native String getOpenVPN3GitVersion();

    public static boolean isRoboUnitTest() {
        return "robolectric".equals(Build.FINGERPRINT); }

    public static native byte[] rsasign(byte[] input, int pkey, boolean pkcs1padding) throws InvalidKeyException;

    static native void jniclose(int fdint);

    private static native String getJNIAPI();

    static {
        if (!isRoboUnitTest()) {
            System.loadLibrary("opvpnutil");
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
                System.loadLibrary("jbcrypto");

        }
    }

}