/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.security.PublicKey;
import oracle.security.pki.internal.pqc.keys.OraclePKIPQCPublicKey;
import oracle.security.pki.internal.pqc.util.OraclePKIPQCKeyPair;
import oracle.security.pki.internal.pqc.util.PQCUtils;
import org.junit.jupiter.api.Test;

public class PQCUtilsTest {
    @Test
    void validatesKnownPqcAlgorithmIdentifier() {
        assertThatCode(() -> PQCUtils.a("ML-DSA-44")).doesNotThrowAnyException();
    }

    @Test
    void readsNamedParametersFromMlDsaPublicKey() throws Exception {
        OraclePKIPQCKeyPair keyPair = OraclePKIPQCKeyPair.a("ML-DSA-44");
        PublicKey publicKey = ((OraclePKIPQCPublicKey) keyPair.getPublicKey()).a();

        assertThat(PQCUtils.a(publicKey, "Public")).isEqualTo("ML-DSA-44");
        assertThat(PQCUtils.b(publicKey)).isEqualTo(1312);
    }
}
