package com.fanok.audiobooks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** SEC-06: the bundled trust anchor must be the genuine, self-signed ISRG Root X1 (checked by fingerprint). */
public class IsrgRootTest {

    private static final String ISRG_ROOT_X1_SHA256 =
            "96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6";

    @Test
    public void bundledAnchorIsIsrgRootX1() throws Exception {
        X509Certificate cert;
        try (FileInputStream in = new FileInputStream("src/main/res/raw/isrg_root_x1.txt")) {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02X", b));
        }
        assertEquals(ISRG_ROOT_X1_SHA256, hex.toString());
        assertEquals("CN=ISRG Root X1, O=Internet Security Research Group, C=US", cert.getSubjectX500Principal().getName().replace(",", ", "));
        assertEquals(cert.getSubjectX500Principal(), cert.getIssuerX500Principal());
    }
}
