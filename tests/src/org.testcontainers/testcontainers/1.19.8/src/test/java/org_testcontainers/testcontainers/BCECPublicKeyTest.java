/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BCECPublicKeyTest {
    @Test
    void serializesProviderEcPublicKeys() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        PublicKey restored = SerializationUtils.roundtrip(pair.getPublic());

        assertThat(restored.getAlgorithm()).isEqualTo("EC");
        assertThat(restored.getEncoded()).containsExactly(pair.getPublic().getEncoded());
    }
}
