/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.OracleSecretStore;
import oracle.security.pki.OracleSecretStoreTextUI;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleSecretStoreMainTextUITest {
    @Test
    void initializesCommandUiAndUsesWalletCredentialStore() throws Exception {
        new OracleSecretStoreTextUI();
        OracleWallet wallet = new OracleWallet();
        wallet.create("wallet-password-01".toCharArray());
        OracleSecretStore store = wallet.getSecretStore();

        store.createUserCredential(
                "database", "service", "database-user", "database-password".toCharArray());

        assertThat(store.getUsernameCredential("database", "service"))
                .isEqualTo("database-user");
    }
}
