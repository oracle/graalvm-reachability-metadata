/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.properties.OrderedProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the ordered properties subsystem for generating deployment configuration.
 */
public class OrderedDeploymentPropertiesTest {
    @Test
    @Timeout(60)
    public void preservesConfigurationOrderWhileUpdatingRuntimeSettings() {
        OrderedProperties configuration = new OrderedProperties();
        configuration.setProperty("service.name", "checkout");
        configuration.setProperty("server.port", "8443");
        Map<String, String> runtimeSettings = new LinkedHashMap<>();
        runtimeSettings.put("feature.audit", "enabled");
        runtimeSettings.put("worker.threads", "8");
        configuration.putAll(runtimeSettings);

        assertEquals("checkout", configuration.getProperty("service.name"));
        assertEquals("8443", configuration.getProperty("server.port"));
        assertEquals(List.of("service.name", "server.port", "feature.audit", "worker.threads"),
                new ArrayList<>(configuration.stringPropertyNames()));

        configuration.setProperty("server.port", "9443");
        configuration.remove("feature.audit");
        configuration.setProperty("feature.metrics", "enabled");

        assertEquals("9443", configuration.getProperty("server.port"));
        assertEquals(List.of("service.name", "server.port", "worker.threads", "feature.metrics"),
                new ArrayList<>(configuration.stringPropertyNames()));
        assertTrue(configuration.entrySet().stream()
                .anyMatch(entry -> entry.getKey().equals("feature.metrics")
                        && entry.getValue().equals("enabled")));
    }
}
