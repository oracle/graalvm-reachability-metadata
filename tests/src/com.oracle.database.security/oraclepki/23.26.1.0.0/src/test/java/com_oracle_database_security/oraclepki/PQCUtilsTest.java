/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThatCode;

import oracle.security.pki.internal.pqc.util.PQCUtils;
import org.junit.jupiter.api.Test;

public class PQCUtilsTest {
    @Test
    void validatesKnownPqcAlgorithmIdentifier() {
        assertThatCode(() -> PQCUtils.a("ML-DSA")).doesNotThrowAnyException();
    }
}
