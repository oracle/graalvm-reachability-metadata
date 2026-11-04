/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// YAML configuration loading and hierarchical property access supply an order service's runtime settings.
public class YamlServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsServiceSettingsAndRoundTripsThem() throws ConfigurationException, IOException {
        String yaml = """
                service:
                  name: order-api
                  endpoints:
                    - https://orders.internal/api
                    - https://orders-backup.internal/api
                  retry:
                    attempts: 4
                    backoff-millis: 250
                features:
                  audit: true
                  accepted-regions:
                    - eu-west
                    - us-east
                """;

        YAMLConfiguration configuration = new YAMLConfiguration();
        configuration.read(new StringReader(yaml));

        assertEquals("order-api", configuration.getString("service.name"));
        assertEquals(
                List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                configuration.getList("service.endpoints"));
        assertEquals(4, configuration.getInt("service.retry.attempts"));
        assertEquals(250, configuration.getInt("service.retry.backoff-millis"));
        assertEquals(true, configuration.getBoolean("features.audit"));
        assertEquals(List.of("eu-west", "us-east"), configuration.getList("features.accepted-regions"));

        StringWriter serialized = new StringWriter();
        configuration.write(serialized);
        YAMLConfiguration reloaded = new YAMLConfiguration();
        reloaded.read(new StringReader(serialized.toString()));

        assertEquals(configuration.getString("service.name"), reloaded.getString("service.name"));
        assertEquals(configuration.getList("service.endpoints"), reloaded.getList("service.endpoints"));
        assertEquals(configuration.getBoolean("features.audit"), reloaded.getBoolean("features.audit"));
    }
}
