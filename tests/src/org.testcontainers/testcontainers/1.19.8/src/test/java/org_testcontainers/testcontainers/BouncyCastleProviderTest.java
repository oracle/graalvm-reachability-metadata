/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastleProviderTest {
    @Test
    void loadsAndUsesAProviderDigestImplementation() throws Exception {
        Provider provider = new BouncyCastleProvider();
        MessageDigest digest = MessageDigest.getInstance("SHA-256", provider);

        byte[] value = digest.digest("metadata".getBytes(StandardCharsets.UTF_8));

        assertThat(value).hasSize(32).isNotEqualTo(new byte[32]);
    }
}
