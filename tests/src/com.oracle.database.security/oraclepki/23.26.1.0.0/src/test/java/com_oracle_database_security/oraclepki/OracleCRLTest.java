/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import javax.security.auth.x500.X500Principal;
import oracle.security.pki.OracleCRL;
import oracle.security.pki.OracleWallet;
import oracle.security.pki.internal.cert.CRL;
import org.junit.jupiter.api.Test;

public class OracleCRLTest {
    @Test
    void createsCrlForSelfSignedWalletIdentity() throws Exception {
        new OracleCRL();
        X500Principal principal = new X500Principal("CN=Oracle PKI CRL Test");
        OracleWallet wallet = new OracleWallet();
        wallet.create("wallet-password-01".toCharArray());
        wallet.createSelfSigned(principal, 2048, 1);

        CRL crl = wallet.createCRL(1);

        assertThat(crl.p()).isEqualTo(principal);
        assertThat(crl.B().getIssuerX500Principal()).isEqualTo(principal);
    }
}
