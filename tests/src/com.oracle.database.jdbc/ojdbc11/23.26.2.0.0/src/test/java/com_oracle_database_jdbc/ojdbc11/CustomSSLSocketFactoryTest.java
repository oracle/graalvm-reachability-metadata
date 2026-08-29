/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.diagnostics.CommonDiagnosable;
import oracle.net.nt.CustomSSLSocketFactory;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CustomSSLSocketFactoryTest {
    @TempDir
    Path walletDirectory;

    @Test
    void createsAnSslSocketFactoryFromAnAutoLoginWallet() throws Exception {
        char[] password = "wallet-password".toCharArray();
        try {
            OracleWallet wallet = new OracleWallet();
            wallet.create(password);
            wallet.saveAs(walletDirectory.toString());
            wallet.createSSO();
            wallet.saveSSO();

            Properties properties = new Properties();
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_KEYSTORE,
                    walletDirectory.resolve("cwallet.sso").toString());
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_KEYSTORETYPE,
                    "SSO");

            SSLSocketFactory socketFactory = CustomSSLSocketFactory.getSSLSocketFactory(
                    properties, null, CommonDiagnosable.getInstance());

            assertThat(socketFactory.getDefaultCipherSuites()).isNotEmpty();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
