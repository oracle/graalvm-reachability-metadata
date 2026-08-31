/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleDriver;
import oracle.security.pki.OracleWallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CustomSSLSocketFactoryTest {
    @TempDir
    Path walletDirectory;

    @Test
    void loadsKnownProviderForAnAutoLoginKeyAndTrustStore() throws Exception {
        char[] password = "wallet-password-01".toCharArray();
        try {
            OracleWallet wallet = new OracleWallet();
            wallet.create(password);
            wallet.saveAs(walletDirectory.toString());
            wallet.createSSO();
            wallet.saveSSO();

            Path autoLoginWallet = walletDirectory.resolve("cwallet.sso");
            assertThat(autoLoginWallet).isRegularFile();

            Properties properties = new Properties();
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_KEYSTORE,
                    autoLoginWallet.toString());
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_KEYSTORETYPE,
                    "SSO");
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_TRUSTSTORE,
                    autoLoginWallet.toString());
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_JAVAX_NET_SSL_TRUSTSTORETYPE,
                    "SSO");
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_NET_CONNECT_TIMEOUT, "10000");
            properties.setProperty(
                    OracleConnection.CONNECTION_PROPERTY_THIN_READ_TIMEOUT, "10000");

            ProviderRegistration[] registrations = removeProvidersFor("KeyStore.SSO");
            try {
                assertThat(Security.getProviders("KeyStore.SSO")).isNull();
                int port = reserveUnusedLoopbackPort();
                String url = "jdbc:oracle:thin:@tcps://127.0.0.1:" + port + "/service";

                assertThatThrownBy(() -> new OracleDriver().connect(url, properties))
                        .isInstanceOf(SQLException.class)
                        .hasRootCauseInstanceOf(ConnectException.class);
                assertThat(Security.getProviders("KeyStore.SSO")).isNull();
            } finally {
                restoreProviders(registrations);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static int reserveUnusedLoopbackPort() throws IOException {
        try (ServerSocket serverSocket =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return serverSocket.getLocalPort();
        }
    }

    private static ProviderRegistration[] removeProvidersFor(String filter) {
        Provider[] providers = Security.getProviders(filter);
        if (providers == null) {
            return new ProviderRegistration[0];
        }

        ProviderRegistration[] registrations = new ProviderRegistration[providers.length];
        for (int index = 0; index < providers.length; index++) {
            Provider provider = providers[index];
            registrations[index] =
                    new ProviderRegistration(provider, providerPosition(provider.getName()));
        }
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }
        return registrations;
    }

    private static void restoreProviders(ProviderRegistration[] registrations) {
        for (ProviderRegistration registration : registrations) {
            Security.insertProviderAt(registration.provider, registration.position);
        }
    }

    private static int providerPosition(String providerName) {
        Provider[] providers = Security.getProviders();
        for (int index = 0; index < providers.length; index++) {
            if (providers[index].getName().equals(providerName)) {
                return index + 1;
            }
        }
        return providers.length + 1;
    }

    private static final class ProviderRegistration {
        private final Provider provider;
        private final int position;

        private ProviderRegistration(Provider provider, int position) {
            this.provider = provider;
            this.position = position;
        }
    }
}
