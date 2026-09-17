/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleWalletTest {
    @TempDir Path directory;

    @Test
    void savesAndReopensPasswordProtectedWallet() throws Exception {
        char[] password = "wallet-password-01".toCharArray();
        OracleWallet wallet = new OracleWallet();
        wallet.create(password);
        wallet.saveAs(directory.toString());

        OracleWallet reopened = new OracleWallet();
        reopened.open(directory.toString(), password);

        assertThat(reopened.getLocation()).isEqualTo(directory.toString());
        assertThat(reopened.getKeyStore().size()).isZero();
    }
}
