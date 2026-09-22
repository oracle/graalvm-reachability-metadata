/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;
import oracle.security.pki.OraclePKIProvider;
import org.junit.jupiter.api.Test;

public class OracleKeyStoreSpiTest {
    @Test
    void createsEmptyOraclePkcs12KeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12", new OraclePKIProvider());
        keyStore.load(null, "wallet-password-01".toCharArray());

        assertThat(keyStore.size()).isZero();
        assertThat(keyStore.getProvider().getName()).isEqualTo("OraclePKI");
    }
}
