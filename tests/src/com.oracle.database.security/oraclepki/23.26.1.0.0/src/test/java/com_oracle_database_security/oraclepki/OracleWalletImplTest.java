/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleWalletImplTest {
    @Test
    void roundTripsWalletThroughEncodedArray() throws Exception {
        char[] password = "wallet-password-01".toCharArray();
        OracleWallet wallet = new OracleWallet();
        wallet.create(password);
        byte[] encoded = wallet.getWalletArrayB(false);

        OracleWallet restored = new OracleWallet();
        restored.setWalletArray(encoded, password);

        assertThat(encoded).isNotEmpty();
        assertThat(restored.getKeyStore().size()).isZero();
    }
}
