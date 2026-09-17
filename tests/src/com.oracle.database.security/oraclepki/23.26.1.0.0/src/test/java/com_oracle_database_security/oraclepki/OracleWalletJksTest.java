/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import oracle.security.pki.OracleWalletJks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleWalletJksTest {
    @TempDir Path directory;

    @Test
    void savesAndReopensJksWallet() throws Exception {
        char[] password = "wallet-password-01".toCharArray();
        Path walletFile = directory.resolve("wallet.jks");
        OracleWalletJks wallet = new OracleWalletJks();
        wallet.create(password);
        wallet.saveAs(walletFile.toString());

        OracleWalletJks reopened = new OracleWalletJks();
        reopened.open(walletFile.toString(), password);

        assertThat(reopened.exists(walletFile.toString())).isTrue();
        assertThat(reopened.getKeyStore().size()).isZero();
    }
}
