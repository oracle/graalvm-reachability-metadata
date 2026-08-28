/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.AlgorithmParameters;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProvECAnonymous55Test {
    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void resolvesANamedCurveFromACompatibleParameterSpec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance(
            "EC", BouncyCastleFipsProvider.PROVIDER_NAME);

        parameters.init(new NamedCurveSpec("secp256r1"));
        ECParameterSpec restoredParameters = parameters.getParameterSpec(ECParameterSpec.class);

        assertThat(restoredParameters.getCurve().getField().getFieldSize()).isEqualTo(256);
        assertThat(restoredParameters.getOrder().signum()).isEqualTo(1);
    }

    public static final class NamedCurveSpec implements AlgorithmParameterSpec {
        private final String name;

        public NamedCurveSpec(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
