/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import oracle.security.crypto.cert.CRL;
import oracle.security.pki.OraclePKIProvider;
import oracle.security.pki.OraclePKIX509CrlFileStore;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleSSLX509CrlTrustManagerImplTest {
    @TempDir Path directory;

    @Test
    void initializesTrustAndRevocationCheckingWithLocalDirectory() throws Exception {
        OracleWallet wallet = WalletFixtures.identityWallet();
        X509Certificate certificate = wallet.getCert(0, 0);
        wallet.setCRLSignAlgorithm("sha256");
        CRL crl = wallet.createCRL(30);
        String filename = OraclePKIX509CrlFileStore.hashName(crl.getIssuer());
        Files.write(directory.resolve(filename), crl.getEncoded());
        String property = "oracle.jsse.crl.FileLocation";
        String previous = System.setProperty(property, directory.toString());
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance("OracleX509", new OraclePKIProvider());
            factory.init(WalletFixtures.trustStore(certificate));
            X509TrustManager manager = (X509TrustManager) factory.getTrustManagers()[0];

            assertThat(manager.getAcceptedIssuers()).isNotEmpty();
            assertThat(manager.getAcceptedIssuers()[0].getEncoded()).isEqualTo(certificate.getEncoded());
            manager.checkServerTrusted(new X509Certificate[] {certificate}, "RSA");
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
