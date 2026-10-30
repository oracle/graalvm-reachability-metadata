/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.X509Certificate;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleWalletTest {
    @Test
    void preservesIdentityInPasswordProtectedWallet() throws Exception {
        OracleWallet original = WalletFixtures.identityWallet();
        byte[] encoded = original.getWalletArrayB(false);
        OracleWallet restored = new OracleWallet();
        restored.setWalletArray(encoded, WalletFixtures.PASSWORD.toCharArray());

        X509Certificate certificate = restored.getCert(0, 0);
        assertThat(certificate.getSubjectX500Principal()).isEqualTo(WalletFixtures.SUBJECT);
        certificate.verify(certificate.getPublicKey());
        assertThat(restored.getCertCount(0)).isEqualTo(1);
    }
}
