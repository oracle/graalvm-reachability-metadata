/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OraclePKIRSAKeyHelperTest {
    @Test
    void advertisesSupportedRsaSizesAndRejectsInvalidSize() throws Exception {
        assertThat(OracleWallet.getKeysizes()).contains(2048, 4096).isSorted();
        OracleWallet wallet = WalletFixtures.emptyWallet();
        IOException error = assertThrows(IOException.class,
                () -> wallet.addCertRequest(WalletFixtures.SUBJECT, 7));
        assertThat(error.getMessage()).isNotBlank();
    }
}
