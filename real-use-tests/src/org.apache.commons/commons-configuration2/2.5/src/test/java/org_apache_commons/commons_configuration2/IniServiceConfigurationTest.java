/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// INI configuration supplies sectioned service settings for an order service and persists a runtime update.
public class IniServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsSectionsAndRoundTripsUpdatedSettings() throws ConfigurationException, IOException {
        String ini = """
                # service deployment settings
                [service]
                name = order-api
                timeout-seconds = 30
                endpoint = https://orders.internal/api
                endpoint = https://orders-backup.internal/api

                [features]
                audit = true
                accepted-region = eu-west
                accepted-region = us-east
                """;

        INIConfiguration configuration = new INIConfiguration();
        configuration.read(new StringReader(ini));

        assertEquals(Set.of("service", "features"), configuration.getSections());
        assertEquals("order-api", configuration.getString("service.name"));
        assertEquals(30, configuration.getInt("service.timeout-seconds"));
        assertEquals(
                List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                configuration.getList("service.endpoint"));
        assertTrue(configuration.getBoolean("features.audit"));
        assertEquals(List.of("eu-west", "us-east"), configuration.getList("features.accepted-region"));

        SubnodeConfiguration service = configuration.getSection("service");
        assertEquals("order-api", service.getString("name"));
        configuration.setProperty("service.timeout-seconds", 45);

        StringWriter serialized = new StringWriter();
        configuration.write(serialized);

        INIConfiguration reloaded = new INIConfiguration();
        reloaded.read(new StringReader(serialized.toString()));

        assertEquals(45, reloaded.getInt("service.timeout-seconds"));
        assertEquals(configuration.getList("service.endpoint"), reloaded.getList("service.endpoint"));
        assertEquals(configuration.getList("features.accepted-region"), reloaded.getList("features.accepted-region"));
    }
}
