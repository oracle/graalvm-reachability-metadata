/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class ECUtilAnonymous1Test {
    @Test
    void obtainsNamedCurveNamesFromJdkParameterSpecifications() {
        assertThat(ECUtil.getNameFrom(new ECGenParameterSpec("secp256r1"))).isEqualTo("secp256r1");
    }
}
