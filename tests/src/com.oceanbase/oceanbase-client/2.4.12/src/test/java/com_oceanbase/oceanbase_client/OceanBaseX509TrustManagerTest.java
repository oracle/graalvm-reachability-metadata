/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class OceanBaseX509TrustManagerTest extends DatabaseTestSupport {
    @Test
    void authenticatesATlsServerUsingACertificateOnTheClasspath() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("useSsl", "true");
        properties.setProperty("trustServerCertificate", "false");
        properties.setProperty("serverSslCert", "classpath:oceanbase-tls/server.pem");
        try (Connection connection = database.connect("", properties);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(2)).isNotBlank();
            }
            try (ResultSet result = statement.executeQuery("SELECT 'trusted'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("trusted");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
