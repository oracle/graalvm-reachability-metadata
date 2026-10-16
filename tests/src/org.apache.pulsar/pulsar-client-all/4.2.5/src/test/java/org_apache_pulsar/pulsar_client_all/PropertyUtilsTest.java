/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.BeanAccess;
import org.junit.jupiter.api.Test;

public class PropertyUtilsTest {
    @Test
    void discoversPropertiesUsingBeanAndFieldAccessModes() {
        final Yaml beanYaml = new Yaml();
        final BeanConfiguration bean = beanYaml.loadAs("name: events", BeanConfiguration.class);

        final Yaml fieldYaml = new Yaml();
        fieldYaml.setBeanAccess(BeanAccess.FIELD);
        final FieldConfiguration field = fieldYaml.loadAs("name: commands", FieldConfiguration.class);

        assertThat(bean.getName()).isEqualTo("events");
        assertThat(field.name).isEqualTo("commands");
    }

    public static final class BeanConfiguration {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String configuredName) {
            name = configuredName;
        }
    }

    public static final class FieldConfiguration {
        private String name;
    }
}
