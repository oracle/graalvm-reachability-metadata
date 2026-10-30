/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import oracle.security.pki.OraclePKIProvider;
import org.junit.jupiter.api.Test;

public class OracleSSLX509CertTrustManagerImplTest {
    @Test
    void acceptsCertificateFromConfiguredTrustStore() throws Exception {
        X509Certificate certificate = WalletFixtures.certificate();
        TrustManagerFactory factory = TrustManagerFactory.getInstance("OracleX509", new OraclePKIProvider());
        factory.init(WalletFixtures.trustStore(certificate));
        X509TrustManager manager = (X509TrustManager) factory.getTrustManagers()[0];

        assertThat(manager.getAcceptedIssuers()).isNotEmpty();
        assertThat(manager.getAcceptedIssuers()[0].getEncoded()).isEqualTo(certificate.getEncoded());
        manager.checkServerTrusted(new X509Certificate[] {certificate}, "RSA");
    }
}
