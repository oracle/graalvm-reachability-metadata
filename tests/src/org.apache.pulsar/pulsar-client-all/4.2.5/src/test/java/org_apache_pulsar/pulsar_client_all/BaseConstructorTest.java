/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;

public class BaseConstructorTest {
    @Test
    void allocatesArraysAndJavaBeansForYamlNodes() {
        final Yaml yaml = new Yaml();
        final String[] names = yaml.loadAs("[one, two, three]", String[].class);
        final Configuration configuration = yaml.loadAs("name: events", Configuration.class);

        assertThat(names).containsExactly("one", "two", "three");
        assertThat(configuration.getName()).isEqualTo("events");
    }

    public static final class Configuration {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String configuredName) {
            name = configuredName;
        }
    }
}
