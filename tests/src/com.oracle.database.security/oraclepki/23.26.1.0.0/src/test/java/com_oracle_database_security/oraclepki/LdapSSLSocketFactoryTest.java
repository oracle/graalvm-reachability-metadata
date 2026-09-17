/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.net.SocketFactory;
import oracle.security.pki.OracleWallet;
import oracle.security.pki.ldap.LdapSSLSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LdapSSLSocketFactoryTest {
    @TempDir Path directory;

    @Test
    void createsDefaultSslSocketFactoryThroughConfiguredJcaFactories() {
        SocketFactory socketFactory = new LdapSSLSocketFactory();

        assertThat(socketFactory).isInstanceOf(LdapSSLSocketFactory.class);
    }

    @Test
    void createsSslSocketFactoryFromPasswordProtectedWallet() throws Exception {
        String password = "wallet-password-01";
        OracleWallet wallet = new OracleWallet();
        wallet.create(password.toCharArray());
        Path walletFile = directory.resolve("ldap-wallet.p12");
        Files.write(walletFile, wallet.getWalletArrayB(false));

        Properties configuration = new Properties();
        configuration.put(LdapSSLSocketFactory.WALLET_LOCATION, walletFile.toString());
        configuration.put(LdapSSLSocketFactory.WALLET_PASSWORD, password);
        SocketFactory socketFactory = new LdapSSLSocketFactory(configuration);

        assertThat(walletFile).isNotEmptyFile();
        assertThat(socketFactory).isInstanceOf(LdapSSLSocketFactory.class);
    }
}
