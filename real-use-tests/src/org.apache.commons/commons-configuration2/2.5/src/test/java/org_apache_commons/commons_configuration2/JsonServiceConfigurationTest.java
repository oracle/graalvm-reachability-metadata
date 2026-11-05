/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.JSONConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// JSON configuration supplies hierarchical settings for an order service and persists a runtime update.
public class JsonServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsAndUpdatesServiceSettings() throws ConfigurationException, IOException {
        String json = """
                {
                  "service": {
                    "name": "order-api",
                    "timeout-seconds": 30,
                    "endpoints": [
                      "https://orders.internal/api",
                      "https://orders-backup.internal/api"
                    ]
                  },
                  "features": {
                    "audit": true,
                    "regions": ["eu-west", "us-east"]
                  }
                }
                """;

        JSONConfiguration configuration = new JSONConfiguration();
        configuration.read(new StringReader(json));

        assertEquals("order-api", configuration.getString("service.name"));
        assertEquals(30, configuration.getInt("service.timeout-seconds"));
        assertEquals(
                List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                configuration.getList("service.endpoints"));
        assertTrue(configuration.getBoolean("features.audit"));
        assertEquals(List.of("eu-west", "us-east"), configuration.getList("features.regions"));

        configuration.setProperty("service.timeout-seconds", 45);
        StringWriter serialized = new StringWriter();
        configuration.write(serialized);

        JSONConfiguration reloaded = new JSONConfiguration();
        reloaded.read(new StringReader(serialized.toString()));

        assertEquals(45, reloaded.getInt("service.timeout-seconds"));
        assertEquals(
                configuration.getList("service.endpoints"),
                reloaded.getList("service.endpoints"));
        assertEquals(
                configuration.getList("features.regions"),
                reloaded.getList("features.regions"));
    }
}
