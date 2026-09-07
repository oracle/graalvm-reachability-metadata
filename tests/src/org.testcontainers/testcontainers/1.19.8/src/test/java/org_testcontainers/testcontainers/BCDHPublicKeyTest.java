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

import javax.crypto.spec.DHPublicKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BCDHPublicKeyTest {
    private static final BigInteger MODP_GROUP_2_PRIME = new BigInteger(
        """
        FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08
        8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431
        B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C
        42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B
        1FE649286651ECE65381FFFFFFFFFFFFFFFF
        """.replaceAll("\\s", ""),
        16
    );

    @Test
    void serializesProviderDhPublicKeys() throws Exception {
        Provider provider = new BouncyCastleProvider();
        KeyFactory factory = KeyFactory.getInstance("DH", provider);
        PublicKey original = factory.generatePublic(
            new DHPublicKeySpec(BigInteger.valueOf(4), MODP_GROUP_2_PRIME, BigInteger.TWO)
        );

        PublicKey restored = SerializationUtils.roundtrip(original);

        assertThat(restored.getAlgorithm()).isEqualTo("DH");
        assertThat(restored.getEncoded()).containsExactly(original.getEncoded());
    }
}
