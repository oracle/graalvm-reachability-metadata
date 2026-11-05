/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.CombinedConfiguration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.tree.OverrideCombiner;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Combined configurations layer deployment overrides over shared application defaults.
public class CombinedApplicationConfigurationTest {
    @Test
    @Timeout(60)
    public void appliesDeploymentOverridesWhileKeepingDefaults() throws ConfigurationException, IOException {
        PropertiesConfiguration defaults = new PropertiesConfiguration();
        defaults.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        defaults.read(new StringReader("""
                service.name=order-api
                service.timeout.seconds=30
                features.audit=true
                features.regions=eu-west,us-east
                """));

        MapConfiguration deployment = new MapConfiguration(Map.of(
                "service.timeout.seconds", 45,
                "features.audit", false));

        CombinedConfiguration configuration = new CombinedConfiguration(new OverrideCombiner());
        configuration.addConfiguration(deployment, "deployment");
        configuration.addConfiguration(defaults, "defaults");

        assertEquals("order-api", configuration.getString("service.name"));
        assertEquals(45, configuration.getInt("service.timeout.seconds"));
        assertFalse(configuration.getBoolean("features.audit"));
        assertEquals(List.of("eu-west", "us-east"), configuration.getList("features.regions"));
        assertTrue(configuration.containsKey("service.name"));
        assertEquals(List.of("deployment", "defaults"), configuration.getConfigurationNameList());
    }
}
