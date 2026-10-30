/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.Security;
import java.util.Properties;
import oracle.security.pki.OracleWallet;
import oracle.security.pki.ldap.LdapSSLSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LdapSSLSocketFactoryTest {
    @TempDir Path directory;

    @Test
    void validatesSocketPortAfterInitializingFromWallet() throws Exception {
        OracleWallet wallet = WalletFixtures.emptyWallet();
        Path walletFile = Files.write(directory.resolve("ldap.p12"), wallet.getWalletArrayB(false));
        Properties configuration = new Properties();
        configuration.setProperty(LdapSSLSocketFactory.WALLET_LOCATION, walletFile.toString());
        configuration.setProperty(LdapSSLSocketFactory.WALLET_PASSWORD, WalletFixtures.PASSWORD);
        Provider previous = Security.getProvider("OraclePKI");
        try {
            LdapSSLSocketFactory factory = new LdapSSLSocketFactory(configuration);
            assertThrows(IllegalArgumentException.class, () -> factory.createSocket("localhost", -1));
        } finally {
            if (previous == null) {
                Security.removeProvider("OraclePKI");
            }
        }
    }
}
