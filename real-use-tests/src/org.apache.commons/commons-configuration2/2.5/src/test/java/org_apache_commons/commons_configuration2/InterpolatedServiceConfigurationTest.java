/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.interpol.ConfigurationInterpolator;
import org.apache.commons.configuration2.interpol.Lookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Configuration interpolation supplies tenant-specific service settings from an application-provided lookup.
public class InterpolatedServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void resolvesTenantSettingsInApplicationConfiguration() {
        MapConfiguration configuration = new MapConfiguration(Map.of(
                "service.endpoint", "https://${tenant:region}.orders.example/api",
                "service.name", "${tenant:name}-orders",
                "service.timeout-seconds", 30));

        ConfigurationInterpolator interpolator = new ConfigurationInterpolator();
        interpolator.registerLookup("tenant", new TenantLookup(Map.of(
                "region", "eu-west",
                "name", "fulfillment")));
        configuration.setInterpolator(interpolator);

        assertEquals("https://eu-west.orders.example/api", configuration.getString("service.endpoint"));
        assertEquals("fulfillment-orders", configuration.getString("service.name"));
        assertEquals(30, configuration.getInt("service.timeout-seconds"));
    }

    private static final class TenantLookup implements Lookup {
        private final Map<String, String> values;

        private TenantLookup(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Object lookup(String key) {
            return values.get(key);
        }
    }
}
