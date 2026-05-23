/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.Buffer;
import com.jcraft.jsch.HASH;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyExchange;
import com.jcraft.jsch.Session;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;

public class KeyExchangeTest {
    private static final String RSA_HOST_KEY_BLOB = "AAAAB3NzaC1yc2EAAAADAQABAAAAQADYk7JzM0lKgx+jrAs6fDMDgKA6izClr"
            + "P9kWhfCwhPMehrVApeM08gj3k7aWK5OIwQk+p5P4JewzLqAA23C+R8=";

    @Test
    void formatsNegotiatedHostKeyFingerprintUsingConfiguredHashImplementation() throws Exception {
        assertThat(new FingerprintHash()).isNotNull();
        JSch.setConfig(md5Config());
        byte[] hostKeyBlob = rsaHostKeyBlob();
        Session session = new JSch().getSession("fingerprint-coverage", "example.test", 22);
        KeyExchange keyExchange = new StaticHostKeyExchange(session, hostKeyBlob);

        String fingerprint = keyExchange.getFingerPrint();

        assertThat(fingerprint).isEqualTo(md5Fingerprint(hostKeyBlob));
    }

    private static Hashtable<String, String> md5Config() {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("md5", FingerprintHash.class.getName());
        return config;
    }

    private static byte[] rsaHostKeyBlob() {
        return Base64.getDecoder().decode(RSA_HOST_KEY_BLOB);
    }

    private static String md5Fingerprint(byte[] hostKeyBlob) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(hostKeyBlob);
        StringBuilder fingerprint = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                fingerprint.append(':');
            }
            int value = digest[i] & 0xff;
            if (value < 0x10) {
                fingerprint.append('0');
            }
            fingerprint.append(Integer.toHexString(value));
        }
        return fingerprint.toString();
    }

    public static final class FingerprintHash implements HASH {
        private MessageDigest messageDigest;

        @Override
        public void init() throws Exception {
            messageDigest = MessageDigest.getInstance("MD5");
        }

        @Override
        public int getBlockSize() {
            return 16;
        }

        @Override
        public void update(byte[] buffer, int offset, int length) throws Exception {
            messageDigest.update(buffer, offset, length);
        }

        @Override
        public byte[] digest() throws Exception {
            return messageDigest.digest();
        }
    }

    private static final class StaticHostKeyExchange extends KeyExchange {
        StaticHostKeyExchange(Session session, byte[] hostKey) {
            this.session = session;
            this.K_S = hostKey;
        }

        @Override
        public void init(Session session, byte[] serverVersion, byte[] clientVersion, byte[] serverKexInit,
                byte[] clientKexInit) {
            this.session = session;
        }

        @Override
        public boolean next(Buffer buffer) {
            return true;
        }

        @Override
        public String getKeyType() {
            return "ssh-rsa";
        }

        @Override
        public int getState() {
            return STATE_END;
        }
    }
}
