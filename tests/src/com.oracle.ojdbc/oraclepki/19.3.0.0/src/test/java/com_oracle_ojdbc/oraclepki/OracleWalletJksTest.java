/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import oracle.security.pki.OracleWalletJks;
import org.junit.jupiter.api.Test;

public class OracleWalletJksTest {
    @Test
    void preservesTrustedCertificateInJksWallet() throws Exception {
        X509Certificate certificate = WalletFixtures.certificate();
        OracleWalletJks wallet = new OracleWalletJks();
        wallet.create(WalletFixtures.PASSWORD.toCharArray());
        KeyStore store = wallet.getKeyStore();
        store.setCertificateEntry("trusted", certificate);
        wallet.setKeyStore(store);

        OracleWalletJks restored = new OracleWalletJks();
        restored.setWalletArray(wallet.getWalletArrayB(false), WalletFixtures.PASSWORD.toCharArray());
        assertThat(restored.getKeyStore().getCertificate("trusted").getEncoded())
                .isEqualTo(certificate.getEncoded());
    }
}
