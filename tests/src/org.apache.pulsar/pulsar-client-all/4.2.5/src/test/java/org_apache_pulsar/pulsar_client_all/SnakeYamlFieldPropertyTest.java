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
    void readsAndWritesFieldsWhenFieldAccessIsSelected() {
        final Yaml yaml = new Yaml();
        yaml.setBeanAccess(BeanAccess.FIELD);
        final FieldConfiguration configuration = yaml.loadAs("topic: events", FieldConfiguration.class);

        assertThat(configuration.topic).isEqualTo("events");
        assertThat(yaml.dumpAsMap(configuration)).contains("topic: events");
    }

    public static final class FieldConfiguration {
        private String topic;
    }
}
