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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileLockProviderTest {
    @TempDir Path directory;

    @Test
    void releasesWalletFileLocksAfterReadsAndWrites() throws Exception {
        OracleWallet wallet = WalletFixtures.identityWallet();
        wallet.saveAs(directory.toString());
        OracleWallet first = new OracleWallet();
        first.open(directory.toString(), WalletFixtures.PASSWORD.toCharArray());
        first.save();
        OracleWallet second = new OracleWallet();
        second.open(directory.toString(), WalletFixtures.PASSWORD.toCharArray());

        assertThat(second.getCert(0, 0).getEncoded()).isEqualTo(wallet.getCert(0, 0).getEncoded());
    }
}
