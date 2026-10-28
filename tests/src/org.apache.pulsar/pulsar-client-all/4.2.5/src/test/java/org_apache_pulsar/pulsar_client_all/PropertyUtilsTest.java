/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.BeanAccess;
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.PropertyUtils;
import org.junit.jupiter.api.Test;

public class PropertyUtilsTest {
    @Test
    void discoversBeanAndFieldProperties() {
        PropertyUtils properties = new PropertyUtils();

        assertThat(properties.getProperties(Settings.class))
                .extracting(property -> property.getName())
                .contains("name");
        properties.setBeanAccess(BeanAccess.FIELD);
        assertThat(properties.getProperties(Settings.class))
                .extracting(property -> property.getName())
                .contains("fieldOnly");
    }

    public static class Settings {
        private String fieldOnly;
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }
    }
}
