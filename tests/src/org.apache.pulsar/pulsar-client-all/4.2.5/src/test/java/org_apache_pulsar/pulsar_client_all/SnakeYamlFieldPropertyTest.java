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

public class SnakeYamlFieldPropertyTest {

    @Test
    void readsAndWritesAFieldBackedConfiguration() {
        final Yaml yaml = new Yaml();
        yaml.setBeanAccess(BeanAccess.FIELD);

        final FieldConfiguration configuration =
                yaml.loadAs("topic: orders\npartitions: 4\n", FieldConfiguration.class);
        final String rendered = yaml.dumpAsMap(configuration);

        assertThat(configuration.topic).isEqualTo("orders");
        assertThat(configuration.partitions).isEqualTo(4);
        assertThat(rendered).contains("topic: orders", "partitions: 4");
    }

    public static final class FieldConfiguration {
        private String topic;
        private int partitions;
    }
}
