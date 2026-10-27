/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import oracle.security.pki.internal.pqc.keys.OraclePKIMLDSAPublicKey;
import oracle.security.pki.util.CryptoUtils;
import org.junit.jupiter.api.Test;

public class PQCUtilsTest {
    @Test
    void determinesMlDsaPublicKeyLengthThroughCryptoApi() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-DSA");
        generator.initialize(NamedParameterSpec.ML_DSA_44);
        PublicKey generatedKey = generator.generateKeyPair().getPublic();
        PublicKey namedKey =
                new NamedParameterPublicKey(generatedKey, NamedParameterSpec.ML_DSA_44);
        PublicKey oracleKey = new OraclePKIMLDSAPublicKey(namedKey);

        assertThat(CryptoUtils.getPublicKeyBitLength(oracleKey)).isEqualTo(1312);
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
