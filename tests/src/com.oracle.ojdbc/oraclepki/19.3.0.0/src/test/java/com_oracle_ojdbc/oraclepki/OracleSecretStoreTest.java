/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.OracleSecretStore;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleSecretStoreTest {
    @Test
    void storesUpdatesAndDeletesNamedSecrets() throws Exception {
        OracleWallet wallet = WalletFixtures.emptyWallet();
        OracleSecretStore store = wallet.getSecretStore();
        store.setSecret("api-token", "first-token".toCharArray());
        assertThat(store.containsAlias("api-token")).isTrue();
        assertThat(store.getSecret("api-token")).isEqualTo("first-token".toCharArray());
        store.setSecret("api-token", "replacement-token".toCharArray());
        assertThat(store.getSecret("api-token")).isEqualTo("replacement-token".toCharArray());
        store.deleteSecret("api-token");
        assertThat(store.containsAlias("api-token")).isFalse();
    }
}
