package com.fanok.audiobooks.source;

import static com.fanok.audiobooks.source.Json.object;
import static com.fanok.audiobooks.source.Json.string;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okio.ByteString;

/**
 * {@code CryptoJS.AES} with a passphrase, in the JSON format the akniga-family players exchange
 * ({@code {"ct": base64, "iv": hex, "s": hex salt}}): OpenSSL's EVP_BytesToKey (MD5, one round) derives the AES-256 key
 * and the iv from the passphrase and the salt; CBC with PKCS7 padding. Pure and static.
 */
final class CryptoJs {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoJs() {
    }

    /** {@code text} encrypted with a random salt. */
    static String encrypt(String text, String passphrase) {
        byte[] salt = new byte[8];
        RANDOM.nextBytes(salt);
        return encrypt(text, passphrase, salt);
    }

    /** {@code text} encrypted with {@code salt} (8 bytes). */
    static String encrypt(String text, String passphrase, byte[] salt) {
        byte[] keyIv = evpBytesToKey(passphrase, salt);
        byte[] iv = Arrays.copyOfRange(keyIv, 32, 48);
        JsonObject json = new JsonObject();
        json.addProperty("ct", ByteString.of(aes(Cipher.ENCRYPT_MODE, keyIv, iv,
                text.getBytes(StandardCharsets.UTF_8))).base64());
        json.addProperty("iv", ByteString.of(iv).hex());
        json.addProperty("s", ByteString.of(salt).hex());
        return json.toString();
    }

    /**
     * The text {@code json} holds. A missing salt reads as zeros and an iv that is not 16 hex bytes as the derived one;
     * anything that does not decrypt is an {@link IllegalStateException}.
     */
    static String decrypt(String json, String passphrase) {
        JsonObject o = object(json);
        String saltHex = string(o, "s");
        String ivHex = string(o, "iv");
        byte[] keyIv = evpBytesToKey(passphrase, saltHex.isEmpty() ? new byte[8] : hexToBytes(saltHex));
        byte[] iv = ivHex.length() == 32 ? hexToBytes(ivHex) : Arrays.copyOfRange(keyIv, 32, 48);
        byte[] ct = Players.decodeBase64(string(o, "ct"), Players.BASE64);
        return new String(aes(Cipher.DECRYPT_MODE, keyIv, iv, ct), StandardCharsets.UTF_8);
    }

    private static byte[] aes(int mode, byte[] keyIv, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(Arrays.copyOfRange(keyIv, 0, 32), "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CryptoJS AES failed", e);
        }
    }

    /** 48 bytes, the key and then the iv: {@code D1 = MD5(pass + salt)}, {@code Di = MD5(D(i-1) + pass + salt)}. */
    static byte[] evpBytesToKey(String passphrase, byte[] salt) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] pass = passphrase.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[48];
            byte[] block = new byte[0];
            for (int filled = 0; filled < result.length; filled += block.length) {
                md5.update(block);
                md5.update(pass);
                md5.update(salt);
                block = md5.digest();
                System.arraycopy(block, 0, result, filled, Math.min(block.length, result.length - filled));
            }
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hex pairs to bytes; a trailing odd digit is ignored. */
    static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((Character.digit(hex.charAt(2 * i), 16) << 4) + Character.digit(hex.charAt(2 * i + 1), 16));
        }
        return data;
    }
}
