/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import oracle.security.pki.OraclePKIProvider;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;

public class OracleKeyStoreSpiTest {
    @Test
    void storesAndLoadsTrustedCertificatesThroughJca() throws Exception {
        X509Certificate certificate = WalletFixtures.certificate();
        KeyStore store = WalletFixtures.trustStore(certificate);
        OracleWallet wallet = WalletFixtures.emptyWallet();
        wallet.setKeyStore(store);

        KeyStore restored = KeyStore.getInstance("PKCS12", new OraclePKIProvider());
        try (ByteArrayInputStream input = new ByteArrayInputStream(wallet.getWalletArrayB(false))) {
            restored.load(input, WalletFixtures.PASSWORD.toCharArray());
        }
        String alias = restored.getCertificateAlias(certificate);
        assertThat(alias).isNotBlank();
        assertThat(restored.isCertificateEntry(alias)).isTrue();
        assertThat(restored.getCertificate(alias).getEncoded()).isEqualTo(certificate.getEncoded());
        restored.deleteEntry(alias);
        assertThat(restored.containsAlias(alias)).isFalse();
    }
}
