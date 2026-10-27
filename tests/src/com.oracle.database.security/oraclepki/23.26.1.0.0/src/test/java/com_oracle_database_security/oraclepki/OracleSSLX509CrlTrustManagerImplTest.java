/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.TrustManagerFactory;
import oracle.security.pki.OraclePKIProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OracleSSLX509CrlTrustManagerImplTest {
    @TempDir Path crlDirectory;

    @Test
    void configuresTrustManagerWithLocalCrlStore() throws Exception {
        String property = "oracle.jsse.crl.FileLocation";
        String previous = System.getProperty(property);
        System.setProperty(property, crlDirectory.toString());
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance("OracleX509", new OraclePKIProvider());

            factory.init(keyStore);

            assertThat(factory.getTrustManagers()).hasSize(1);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
