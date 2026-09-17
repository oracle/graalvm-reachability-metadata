/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import oracle.security.pki.OraclePKIProvider;
import org.junit.jupiter.api.Test;

public class OracleSSLX509CertTrustManagerImplTest {
    @Test
    void createsOracleX509TrustManagerForEmptyKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance("OracleX509", new OraclePKIProvider());

        factory.init(keyStore);

        assertThat(factory.getTrustManagers()).hasSize(1);
        assertThat(factory.getTrustManagers()[0]).isInstanceOf(X509TrustManager.class);
    }
}
