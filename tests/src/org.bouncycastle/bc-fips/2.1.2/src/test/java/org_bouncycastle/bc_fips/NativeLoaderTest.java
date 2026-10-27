/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLoaderTest {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void calculatesSha256WithTheFipsProvider() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(
            "SHA-256", BouncyCastleFipsProvider.PROVIDER_NAME);

        byte[] input = "native loader".getBytes(StandardCharsets.UTF_8);
        byte[] hash = digest.digest(input);
        MessageDigest jdkDigest = MessageDigest.getInstance("SHA-256", "SUN");

        assertThat(hash).isEqualTo(jdkDigest.digest(input));
    }
}
