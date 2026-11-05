/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import oracle.net.nt.CustomSSLSocketFactory;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CustomSSLSocketFactoryTest {
    @TempDir
    Path directory;

    @Test
    void loadsAnAutoLoginWalletThroughTheKnownProviderFallback() throws Exception {
        Path file = directory.resolve("cwallet.sso");
        Provider previous = Security.getProvider("OraclePKI");
        try {
            OracleWallet wallet = new OracleWallet();
            wallet.createSSO();
            Files.write(file, wallet.getWalletArrayB(true));
        } finally {
            // Wallet creation registers its provider globally; leave provider selection to the JDBC loader.
            if (previous == null) {
                Security.removeProvider("OraclePKI");
            }
        }

        TrustManager[] managers = CustomSSLSocketFactory.getTrustManagerArray(file.toString(), "",
                "SSO", TrustManagerFactory.getDefaultAlgorithm());

        assertThat(managers).hasSize(1);
        assertThat(managers[0]).isInstanceOf(X509TrustManager.class);
        X509TrustManager trust = (X509TrustManager) managers[0];
        assertThat(trust.getAcceptedIssuers()).isEmpty();
    }
}
