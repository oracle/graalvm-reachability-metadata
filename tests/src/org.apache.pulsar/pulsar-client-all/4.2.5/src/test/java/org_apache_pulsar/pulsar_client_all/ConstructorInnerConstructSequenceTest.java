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

public class ConstructorInnerConstructSequenceTest {
    @Test
    void selectsAndInvokesASequenceConstructor() {
        final Topic topic = new Yaml().loadAs("[events, 4]", Topic.class);

        assertThat(topic.name).isEqualTo("events");
        assertThat(topic.partitions).isEqualTo(4);
    }

    public static final class Topic {
        private final String name;
        private final int partitions;

        public Topic(String name, int partitions) {
            this.name = name;
            this.partitions = partitions;
        }
    }
}
