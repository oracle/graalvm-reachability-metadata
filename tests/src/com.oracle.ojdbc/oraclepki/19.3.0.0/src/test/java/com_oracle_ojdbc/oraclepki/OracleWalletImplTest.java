/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import oracle.security.pki.OracleSecretStore;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleWalletImplTest {
    @TempDir Path directory;

    @Test
    void persistsSecretStoreThroughWalletFile() throws Exception {
        OracleWallet wallet = WalletFixtures.emptyWallet();
        OracleSecretStore secrets = wallet.getSecretStore();
        secrets.setSecret("service-token", "test-token".toCharArray());
        wallet.setSecretStore(secrets);
        wallet.saveAs(directory.toString());

        OracleWallet reopened = new OracleWallet();
        reopened.open(directory.toString(), WalletFixtures.PASSWORD.toCharArray());
        assertThat(reopened.getSecretStore().getSecret("service-token"))
                .isEqualTo("test-token".toCharArray());
    }
}
