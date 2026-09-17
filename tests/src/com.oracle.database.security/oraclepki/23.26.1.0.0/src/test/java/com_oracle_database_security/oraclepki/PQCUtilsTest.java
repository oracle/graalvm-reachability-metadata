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
import java.security.spec.NamedParameterSpec;
import oracle.security.pki.internal.pqc.util.OraclePKIPQCKeyPair;
import oracle.security.pki.internal.pqc.util.PQCUtils;
import org.junit.jupiter.api.Test;

public class PQCUtilsTest {
    @Test
    void validatesKnownPqcAlgorithmIdentifier() {
        assertThatCode(() -> PQCUtils.a("ML-DSA-44")).doesNotThrowAnyException();
    }

    @Test
    void readsNamedParametersFromProviderIndependentPqcKey() throws Exception {
        OraclePKIPQCKeyPair keyPair = OraclePKIPQCKeyPair.a("ML-DSA-44");
        PublicKey publicKey =
                new NamedParameterPublicKey(keyPair.getPublicKey(), NamedParameterSpec.ML_DSA_44);

        assertThat(PQCUtils.a(publicKey, "Public")).isEqualTo("ML-DSA-44");
        assertThat(PQCUtils.b(publicKey)).isEqualTo(1312);
    }

    public static final class NamedParameterPublicKey implements PublicKey {
        private static final long serialVersionUID = 1L;

        private final PublicKey delegate;
        private final NamedParameterSpec params;

        public NamedParameterPublicKey(PublicKey delegate, NamedParameterSpec params) {
            this.delegate = delegate;
            this.params = params;
        }

        public NamedParameterSpec getParams() {
            return params;
        }

        @Override
        public String getAlgorithm() {
            return delegate.getAlgorithm();
        }

        @Override
        public String getFormat() {
            return delegate.getFormat();
        }

        @Override
        public byte[] getEncoded() {
            return delegate.getEncoded();
        }
    }
}
