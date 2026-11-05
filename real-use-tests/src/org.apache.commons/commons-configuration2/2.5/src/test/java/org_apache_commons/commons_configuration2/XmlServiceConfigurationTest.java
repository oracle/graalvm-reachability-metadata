/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// XML configuration provides hierarchical service settings and repeated endpoint values.
public class XmlServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsAndUpdatesServiceSettings() throws ConfigurationException, IOException {
        String xml = """
                <service>
                  <name>order-api</name>
                  <timeout-seconds>30</timeout-seconds>
                  <endpoints>
                    <endpoint>https://orders.internal/api</endpoint>
                    <endpoint>https://orders-backup.internal/api</endpoint>
                  </endpoints>
                  <features>
                    <audit>true</audit>
                    <region>eu-west</region>
                    <region>us-east</region>
                  </features>
                </service>
                """;

        XMLConfiguration configuration = new XMLConfiguration();
        FileHandler fileHandler = new FileHandler(configuration);
        fileHandler.load(new StringReader(xml));

        assertEquals("order-api", configuration.getString("name"));
        assertEquals(30, configuration.getInt("timeout-seconds"));
        assertEquals(
                List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                configuration.getList("endpoints.endpoint"));
        assertTrue(configuration.getBoolean("features.audit"));
        assertEquals(List.of("eu-west", "us-east"), configuration.getList("features.region"));

        configuration.setProperty("timeout-seconds", 45);
        StringWriter serialized = new StringWriter();
        fileHandler.save(serialized);

        XMLConfiguration reloaded = new XMLConfiguration();
        FileHandler reloadedFileHandler = new FileHandler(reloaded);
        reloadedFileHandler.load(new StringReader(serialized.toString()));

        assertEquals(45, reloaded.getInt("timeout-seconds"));
        assertEquals(
                configuration.getList("endpoints.endpoint"),
                reloaded.getList("endpoints.endpoint"));
        assertEquals(
                configuration.getList("features.region"),
                reloaded.getList("features.region"));
    }
}
