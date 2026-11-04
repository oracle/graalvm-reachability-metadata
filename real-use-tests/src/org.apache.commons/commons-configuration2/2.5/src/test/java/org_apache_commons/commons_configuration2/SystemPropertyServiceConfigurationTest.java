/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.SystemConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// System-property configuration supplies deployment overrides to an order service.
public class SystemPropertyServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void readsDeploymentOverridesFromSystemProperties() {
        String endpointKey = "order.service.endpoint";
        String timeoutKey = "order.service.timeout-seconds";
        String enabledKey = "order.service.enabled";
        String previousEndpoint = System.getProperty(endpointKey);
        String previousTimeout = System.getProperty(timeoutKey);
        String previousEnabled = System.getProperty(enabledKey);
        try {
            System.setProperty(endpointKey, "https://orders.internal/api");
            System.setProperty(timeoutKey, "45");
            System.setProperty(enabledKey, "true");

            SystemConfiguration configuration = new SystemConfiguration();

            assertEquals("https://orders.internal/api", configuration.getString(endpointKey));
            assertEquals(45, configuration.getInt(timeoutKey));
            assertTrue(configuration.getBoolean(enabledKey));
        } finally {
            restoreProperty(endpointKey, previousEndpoint);
            restoreProperty(timeoutKey, previousTimeout);
            restoreProperty(enabledKey, previousEnabled);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
