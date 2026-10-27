/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import oracle.security.pki.util.CryptoUtils;
import org.junit.jupiter.api.Test;

public class CryptoUtilsTest {
    @Test
    void derivesStableIdentifierFromPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair pair = generator.generateKeyPair();

        byte[] first = CryptoUtils.generateKeyID(pair.getPublic());
        byte[] second = CryptoUtils.generateKeyID(pair.getPublic());

        assertThat(first).isNotEmpty().containsExactly(second);
    }
}
