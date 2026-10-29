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
import com.jcraft.jsch.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class HostKeyTest {
    @Test
    void fingerprintsAndEncodesAnSshHostKey() throws Exception {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        try {
            byte[] blob = pair.getPublicKeyBlob();
            HostKey host = new HostKey("ssh.example", blob);
            assertThat(host.getHost()).isEqualTo("ssh.example");
            assertThat(host.getType()).isEqualTo("ssh-rsa");
            assertThat(host.getKey()).isEqualTo(Base64.getEncoder().encodeToString(blob));
            assertThat(host.getFingerPrint(jsch)).isEqualTo(Fingerprints.md5(blob));
        } finally {
            pair.dispose();
        }
    }
}
