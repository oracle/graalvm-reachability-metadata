/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.BeanAccess;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises SnakeYAML's field-access introspection subsystem for binding legacy service settings.
public class FieldAccessConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsSettingsWithoutJavaBeanMutators() {
        String configurationText = """
                serviceName: billing-api
                enabled: true
                regions:
                  - us-east-1
                  - eu-west-1
                """;

        Yaml yaml = new Yaml();
        yaml.setBeanAccess(BeanAccess.FIELD);
        ServiceSettings settings = yaml.loadAs(configurationText, ServiceSettings.class);

        assertEquals("billing-api", settings.serviceName());
        assertEquals(true, settings.enabled());
        assertEquals(List.of("us-east-1", "eu-west-1"), settings.regions());
    }

    public static final class ServiceSettings {
        private String serviceName;
        private boolean enabled;
        private List<String> regions;

        public ServiceSettings() {
        }

        public String serviceName() {
            return serviceName;
        }

        public boolean enabled() {
            return enabled;
        }

        public List<String> regions() {
            return regions;
        }
    }
}
