/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import oracle.security.pki.OracleSecretStore;
import oracle.security.pki.OracleSecretStoreException;
import org.junit.jupiter.api.Test;

public class OracleSecretStoreHelperTest {
    @Test
    void managesMappedUserCredentialsThroughSecretStore() throws Exception {
        OracleSecretStore store = WalletFixtures.emptyWallet().getSecretStore();
        store.createUserCredential("application", "database", "alice", "first-password".toCharArray());
        assertThat(store.getUsernameCredential("application", "database")).isEqualTo("alice");
        assertThat(store.getPasswordCredential("application", "database"))
                .isEqualTo("first-password".toCharArray());
        store.modifyUserCredential("application", "database", "bob", "next-password".toCharArray());
        assertThat(store.getUsernameCredential("application", "database")).isEqualTo("bob");
        assertThat(store.getPasswordCredential("application", "database"))
                .isEqualTo("next-password".toCharArray());
        assertThrows(OracleSecretStoreException.class,
                () -> store.createUserCredential("application", " ", "alice", "password".toCharArray()));
    }
}
