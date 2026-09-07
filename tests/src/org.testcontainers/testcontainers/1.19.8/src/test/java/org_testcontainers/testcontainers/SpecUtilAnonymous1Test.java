/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.pqc.jcajce.provider.util.SpecUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecUtilAnonymous1Test {
    @Test
    void obtainsNamesFromAlgorithmParameterSpecifications() {
        assertThat(SpecUtil.getNameFrom(new ECGenParameterSpec("secp384r1"))).isEqualTo("secp384r1");
    }
}
