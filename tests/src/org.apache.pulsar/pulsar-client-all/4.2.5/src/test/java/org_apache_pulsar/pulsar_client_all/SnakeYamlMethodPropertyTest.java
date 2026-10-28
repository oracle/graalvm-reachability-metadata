/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.LoaderOptions;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.Constructor;
import org.junit.jupiter.api.Test;

public class SnakeYamlMethodPropertyTest {
    @Test
    void writesPropertyThroughSetterAndReadsThroughGetter() {
        Yaml yaml = new Yaml(new Constructor(MethodSettings.class, new LoaderOptions()));

        MethodSettings settings = yaml.load("name: method-value");

        assertThat(settings.getName()).isEqualTo("method-value");
        assertThat(yaml.dump(settings)).contains("name: method-value");
    }

    public static class MethodSettings {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }
    }
}
