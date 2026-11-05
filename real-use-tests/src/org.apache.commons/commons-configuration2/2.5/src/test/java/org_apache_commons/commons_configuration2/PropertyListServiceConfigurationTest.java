/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Property-list configuration lets a desktop service load structured deployment settings and persist an operator update.
public class PropertyListServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsAndPersistsDeploymentSettings() throws ConfigurationException, IOException {
        String propertyList = """
                {
                    service = {
                        name = "order-api";
                        enabled = YES;
                        ports = ( 8080, 8443 );
                    };
                    features = {
                        audit = NO;
                        region = "eu-west";
                    };
                }
                """;

        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.read(new StringReader(propertyList));

        assertEquals("order-api", configuration.getString("service.name"));
        assertTrue(configuration.getBoolean("service.enabled"));
        assertEquals(List.of(8080, 8443), configuration.getList(Integer.class, "service.ports"));
        assertFalse(configuration.getBoolean("features.audit"));
        assertEquals("eu-west", configuration.getString("features.region"));

        configuration.setProperty("service.name", "fulfillment-api");
        StringWriter output = new StringWriter();
        configuration.write(output);

        PropertyListConfiguration reloaded = new PropertyListConfiguration();
        reloaded.read(new StringReader(output.toString()));
        assertEquals("fulfillment-api", reloaded.getString("service.name"));
        assertEquals(List.of(8080, 8443), reloaded.getList(Integer.class, "service.ports"));
    }
}
