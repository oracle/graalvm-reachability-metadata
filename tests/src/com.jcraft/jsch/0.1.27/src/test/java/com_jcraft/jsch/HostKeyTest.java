/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.jce.MD5;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;

public class HostKeyTest {
    private static final String RSA_HOST_KEY_BLOB = "AAAAB3NzaC1yc2EAAAADAQABAAAAQADYk7JzM0lKgx+jrAs6fDMDgKA6izClr"
            + "P9kWhfCwhPMehrVApeM08gj3k7aWK5OIwQk+p5P4JewzLqAA23C+R8=";

    @Test
    void formatsRsaHostKeyFingerprintUsingConfiguredMd5Hash() throws Exception {
        assertThat(new MD5()).isNotNull();
        configureMd5(MD5.class.getName());
        byte[] hostKeyBlob = rsaHostKeyBlob();
        HostKey hostKey = new HostKey("example.test", hostKeyBlob);

        String fingerprint = hostKey.getFingerPrint(new JSch());

        assertThat(hostKey.getHost()).isEqualTo("example.test");
        assertThat(hostKey.getType()).isEqualTo("ssh-rsa");
        assertThat(fingerprint).isEqualTo(md5Fingerprint(hostKeyBlob));
    }

    private static void configureMd5(String md5) {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("md5", md5);
        JSch.setConfig(config);
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
}
