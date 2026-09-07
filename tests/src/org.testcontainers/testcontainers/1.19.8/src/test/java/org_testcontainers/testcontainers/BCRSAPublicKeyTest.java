/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BCRSAPublicKeyTest {
    @Test
    void serializesProviderRsaPublicKeys() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyFactory factory = KeyFactory.getInstance("RSA", provider);
        PublicKey original = factory.generatePublic(
            new RSAPublicKeySpec(BigInteger.valueOf(3233), BigInteger.valueOf(17))
        );

        PublicKey restored = SerializationUtils.roundtrip(original);

        assertThat(restored.getAlgorithm()).isEqualTo("RSA");
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
    }
}
