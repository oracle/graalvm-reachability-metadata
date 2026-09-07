/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.Provider;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePQCProviderTest {
    @Test
    void loadsPostQuantumAlgorithmMappings() {
        Provider provider = new BouncyCastlePQCProvider();

        assertThat(provider.getServices()).isNotEmpty();
        assertThat(provider.getService("KeyPairGenerator", "SPHINCSPLUS")).isNotNull();
    }
}
