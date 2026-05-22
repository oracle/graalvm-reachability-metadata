/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePQCProviderTest {
    @Test
    void providerLoadsPostQuantumAlgorithmMappings() throws Exception {
        Provider previousProvider = Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME);
        if (previousProvider != null) {
            Security.removeProvider(BouncyCastlePQCProvider.PROVIDER_NAME);
        }

        try {
            Provider provider = new BouncyCastlePQCProvider();
            int position = Security.addProvider(provider);

            assertThat(position).isPositive();
            assertThat(provider.getName()).isEqualTo(BouncyCastlePQCProvider.PROVIDER_NAME);
            assertThat(provider.getInfo()).contains("Post-Quantum Security Provider");
            assertThat(provider.get("KeyFactory.Rainbow"))
                .isEqualTo("org.bouncycastle.pqc.jcajce.provider.rainbow.RainbowKeyFactorySpi");
            assertThat(provider.get("KeyPairGenerator.Rainbow"))
                .isEqualTo("org.bouncycastle.pqc.jcajce.provider.rainbow.RainbowKeyPairGeneratorSpi");
            assertThat(provider.get("KeyPairGenerator.McEliecePKCS"))
                .isEqualTo("org.bouncycastle.pqc.jcajce.provider.mceliece.McElieceKeyPairGeneratorSpi$McEliece");
            assertThat(provider.get("Cipher.McEliecePKCS"))
                .isEqualTo("org.bouncycastle.pqc.jcajce.provider.mceliece.McEliecePKCSCipherSpi$McEliecePKCS");

            assertThat(((BouncyCastlePQCProvider) provider).hasAlgorithm("KeyPairGenerator", "Rainbow")).isTrue();
            assertThat(Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME)).isSameAs(provider);
            assertThat(KeyFactory.getInstance("Rainbow", provider).getProvider()).isSameAs(provider);
            assertThat(KeyPairGenerator.getInstance("Rainbow", provider).getProvider()).isSameAs(provider);
        } finally {
            Security.removeProvider(BouncyCastlePQCProvider.PROVIDER_NAME);
            if (previousProvider != null) {
                Security.addProvider(previousProvider);
            }
        }
    }
}
