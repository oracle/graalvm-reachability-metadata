/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class BeanPropertyWriterTest {
    @Test
    void serializesFieldAndGetterBackedProperties() throws Exception {
        final String json = new ObjectMapper().writeValueAsString(new TopicView("events", 4));

        assertThat(json).contains("\"name\":\"events\"");
        assertThat(json).contains("\"partitions\":4");
    }

    public static final class TopicView {
        public String name;
        private final int partitions;

        public TopicView(String name, int partitions) {
            this.name = name;
            this.partitions = partitions;
        }

        public int getPartitions() {
            return partitions;
        }
    }
}
