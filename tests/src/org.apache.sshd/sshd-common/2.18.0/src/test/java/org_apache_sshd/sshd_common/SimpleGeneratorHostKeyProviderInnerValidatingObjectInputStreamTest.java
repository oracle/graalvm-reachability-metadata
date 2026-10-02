/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class SimpleGeneratorHostKeyProviderInnerValidatingObjectInputStreamTest {
    @Test
    void validatesClassesWhileReadingAStoredHostKey() throws Exception {
        Path keyFile = Files.createTempFile("sshd-host-key", ".ser");
        try {
            KeyPair expected = SimpleGeneratorHostKeyProviderTest.writeSerializedKeyPair(keyFile);
            SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(keyFile);
            provider.setAlgorithm("RSA");

            List<KeyPair> loaded = provider.loadKeys(null);

            assertThat(loaded).hasSize(1);
            assertThat(KeyUtils.compareKeys(expected.getPrivate(), loaded.get(0).getPrivate()))
                    .isTrue();
        } finally {
            Files.deleteIfExists(keyFile);
        }
    }
}
