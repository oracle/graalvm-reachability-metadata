/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.OracleSecretStore;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleSecretStoreTest {
    @Test
    void storesAndRetrievesApplicationSecret() throws Exception {
        OracleWallet wallet = new OracleWallet();
        wallet.create("wallet-password-01".toCharArray());
        OracleSecretStore secretStore = wallet.getSecretStore();

        secretStore.setSecret("database.password", "secret-value".toCharArray());

        assertThat(secretStore.containsAlias("database.password")).isTrue();
        assertThat(secretStore.getSecret("database.password"))
                .containsExactly("secret-value".toCharArray());
    }
}
