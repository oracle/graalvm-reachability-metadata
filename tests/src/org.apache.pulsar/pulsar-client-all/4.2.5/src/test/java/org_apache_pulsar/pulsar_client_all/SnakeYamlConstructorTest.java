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

public class SnakeYamlConstructorTest {
    @Test
    void resolvesTheConfiguredRootClassByName() throws Exception {
        final Constructor constructor = new Constructor(Configuration.class.getName(), new LoaderOptions());
        final Configuration configuration = new Yaml(constructor).load("name: events\npartitions: 4\n");

        assertThat(configuration.getName()).isEqualTo("events");
        assertThat(configuration.getPartitions()).isEqualTo(4);
    }

    public static final class Configuration {
        private String name;
        private int partitions;

        public String getName() {
            return name;
        }

        public void setName(String configuredName) {
            name = configuredName;
        }

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int configuredPartitions) {
            partitions = configuredPartitions;
        }
    }
}
