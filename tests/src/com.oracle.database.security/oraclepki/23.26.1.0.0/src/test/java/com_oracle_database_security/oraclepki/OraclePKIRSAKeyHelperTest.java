/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import oracle.security.pki.internal.OraclePKIRSAKeyHelper;
import org.junit.jupiter.api.Test;

public class OraclePKIRSAKeyHelperTest {
    @Test
    void acceptsAdvertisedRsaKeySize() {
        int keySize =
                OraclePKIRSAKeyHelper.getKeySizes().stream()
                        .min(Integer::compareTo)
                        .orElseThrow();

        assertThat(keySize).isPositive();
        assertThatCode(() -> OraclePKIRSAKeyHelper.validateKeysize(keySize))
                .doesNotThrowAnyException();
    }
}
