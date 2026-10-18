/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.BeanAccess;
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.Property;
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.PropertyUtils;
import org.junit.jupiter.api.Test;

public class PropertyUtilsTest {

    @Test
    void discoversPrivateFieldsWhenFieldAccessIsRequested() {
        final PropertyUtils properties = new PropertyUtils();
        properties.setBeanAccess(BeanAccess.FIELD);

        final Property topic = properties.getProperty(FieldConfiguration.class, "topic");
        final Property partitions = properties.getProperty(FieldConfiguration.class, "partitions");

        assertThat(topic.getType()).isEqualTo(String.class);
        assertThat(partitions.getType()).isEqualTo(int.class);
        assertThat(properties.getProperties(FieldConfiguration.class))
                .extracting(Property::getName)
                .containsExactlyInAnyOrder("topic", "partitions");
    }

    public static final class FieldConfiguration {
        private String topic;
        private int partitions;
    }
}
