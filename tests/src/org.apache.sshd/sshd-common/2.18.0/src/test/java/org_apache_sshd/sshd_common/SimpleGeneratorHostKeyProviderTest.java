/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.List;

import org.apache.sshd.common.config.keys.BuiltinIdentities;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class SimpleGeneratorHostKeyProviderTest {
    @Test
    void loadsASerializedHostKeyThroughTheProvider() throws Exception {
        Path keyFile = Files.createTempFile("sshd-host-key", ".ser");
        try {
            KeyPair expected = writeSerializedKeyPair(keyFile);
            SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(keyFile);
            provider.setAlgorithm("RSA");

            List<KeyPair> loaded = provider.loadKeys(null);

            assertThat(loaded).singleElement().satisfies(actual ->
                    assertThat(KeyUtils.compareKeys(expected.getPublic(), actual.getPublic()))
                            .isTrue());
        } finally {
            Files.deleteIfExists(keyFile);
        }
    }

    static KeyPair writeSerializedKeyPair(Path keyFile)
            throws IOException, GeneralSecurityException {
        KeyPair keyPair = KeyUtils.generateKeyPair(
                BuiltinIdentities.RSA.getSupportedKeyTypes().first(), 2048);
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(keyFile))) {
            output.writeObject(keyPair);
        }
        return keyPair;
    }
}
