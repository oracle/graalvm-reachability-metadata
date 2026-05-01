/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class HostKeyTest {
    private static final byte[] HOST_KEY =
            "coverage host key material".getBytes(StandardCharsets.UTF_8);

    @Test
    void getFingerPrintUsesConfiguredSha256HashImplementation() throws Exception {
        String previousFingerprintHash = JSch.getConfig("FingerprintHash");
        JSch.setConfig("FingerprintHash", "sha256");
        try {
            HostKey hostKey = new HostKey("example.test", HostKey.SSHRSA, HOST_KEY, "coverage");

            String fingerprint = hostKey.getFingerPrint(new JSch());

            assertThat(fingerprint).isEqualTo(expectedSha256Fingerprint());
            assertThat(hostKey.getHost()).isEqualTo("example.test");
            assertThat(hostKey.getType()).isEqualTo("ssh-rsa");
            assertThat(hostKey.getComment()).isEqualTo("coverage");
        } finally {
            JSch.setConfig("FingerprintHash", previousFingerprintHash);
        }
    }

    private static String expectedSha256Fingerprint() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashedKey = digest.digest(HOST_KEY);
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hashedKey);
    }
}
