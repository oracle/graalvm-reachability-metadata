/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import oracle.jdbc.driver.configuration.OracleConfigurationFileProvider;
import org.junit.jupiter.api.Test;

public class OracleConfigurationFileProviderTest {
    @Test
    void opensConfigurationContentFromTheDriverClasspath() throws Exception {
        OracleConfigurationFileProvider provider = new OracleConfigurationFileProvider();

        try (InputStream input = provider.getInputStream("oracle/jdbc/OracleConnectionCopy.txt")) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("CONNECTION_PROPERTY_USER_NAME");
        }
        assertThat(provider.getType()).isEqualTo("file");
    }
}
