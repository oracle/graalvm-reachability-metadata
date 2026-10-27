/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.internal.OracleSecretStoreHelper;
import org.junit.jupiter.api.Test;

public class OracleSecretStoreHelperTest {
    @Test
    void encodesAndDecodesUserCredential() {
        char[] encoded =
                OracleSecretStoreHelper.createSecretForUserCredential(
                        "database-user", "database-password".toCharArray());

        assertThat(OracleSecretStoreHelper.getUsernameFromUserCredential(encoded))
                .isEqualTo("database-user");
        assertThat(OracleSecretStoreHelper.getPasswordFromUserCredential(encoded))
                .containsExactly("database-password".toCharArray());
    }
}
