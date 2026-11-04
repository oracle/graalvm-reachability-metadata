/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.beanutils.ConfigurationDynaBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Dynamic bean configuration exposes an order service's settings to a plugin API with runtime property names.
public class DynamicServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void readsAndUpdatesPluginSettingsThroughDynamicBean() {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.addProperty("plugin.name", "fraud-check");
        configuration.addProperty("plugin.enabled", true);
        configuration.addProperty("plugin.threshold", 75);
        configuration.addProperty("plugin.tags", "orders");
        configuration.addProperty("plugin.tags", "priority");

        DynaBean pluginSettings = new ConfigurationDynaBean(configuration);

        assertEquals("fraud-check", pluginSettings.get("plugin.name"));
        assertEquals(true, pluginSettings.get("plugin.enabled"));
        assertEquals(75, pluginSettings.get("plugin.threshold"));
        assertEquals("orders", pluginSettings.get("plugin.tags", 0));
        assertEquals("priority", pluginSettings.get("plugin.tags", 1));
        DynaProperty threshold = pluginSettings.getDynaClass().getDynaProperty("plugin.threshold");
        assertNotNull(threshold);
        assertEquals(int.class, threshold.getType());

        pluginSettings.set("plugin.threshold", 90);
        pluginSettings.set("plugin.enabled", false);

        assertEquals(90, configuration.getInt("plugin.threshold"));
        assertEquals(false, configuration.getBoolean("plugin.enabled"));
    }
}
