/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KeyPairTest {
    @TempDir
    Path directory;

    @Test
    void encryptsPrivateKeyAndLoadsItWithThePassphrase() throws Exception {
        JSch jsch = new JSch();
        KeyPair original = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        KeyPair loaded = null;
        try {
            byte[] publicKey = original.getPublicKeyBlob();
            String fingerprint = original.getFingerPrint();
            assertThat(fingerprint).isEqualTo("2048 " + Fingerprints.md5(publicKey));
            original.setPassphrase("key-storage-secret");
            Path privateFile = directory.resolve("identity");
            original.writePrivateKey(privateFile.toString());
            original.writePublicKey(privateFile + ".pub", "stored-key");
            assertThat(Files.readString(privateFile)).contains("Proc-Type: 4,ENCRYPTED", "DES-EDE3-CBC");
            loaded = KeyPair.load(jsch, privateFile.toString(), null);
            assertThat(loaded.isEncrypted()).isTrue();
            assertThat(loaded.decrypt("key-storage-secret")).isTrue();
            assertThat(loaded.isEncrypted()).isFalse();
            assertThat(loaded.getPublicKeyBlob()).containsExactly(publicKey);
            assertThat(loaded.getFingerPrint()).endsWith(" " + Fingerprints.md5(publicKey));
        } finally {
            original.dispose();
            if (loaded != null) {
                loaded.dispose();
            }
        }
    }
}
