/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import oracle.security.pki.OracleWallet;
import oracle.security.pki.textui.OracleWalletTextUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleWalletTextUITest {
    @TempDir Path directory;

    @Test
    void createsPasswordProtectedWalletFromCommandArguments() throws Exception {
        int status = OracleWalletTextUI.command(new String[] {
                "create", "-wallet", directory.toString(), "-pwd", WalletFixtures.PASSWORD
        });
        assertThat(status).isZero();
        OracleWallet wallet = new OracleWallet();
        assertThat(wallet.exists(directory.toString(), false)).isTrue();
        wallet.open(directory.toString(), WalletFixtures.PASSWORD.toCharArray());
        assertThat(wallet.getCertCount(0)).isZero();
    }
}
