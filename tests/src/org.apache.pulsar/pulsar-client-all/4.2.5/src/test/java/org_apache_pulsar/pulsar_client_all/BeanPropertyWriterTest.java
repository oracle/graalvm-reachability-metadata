/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class BeanPropertyWriterTest {
    @Test
    void serializesFieldAndGetterAsArrayElements() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new TopicSummary("orders", 4));
        TopicSummary restored = mapper.readValue(json, TopicSummary.class);

        assertThat(json).startsWith("[").endsWith("]").contains("4", "orders");
        assertThat(restored.partitions).isEqualTo(4);
        assertThat(restored.getName()).isEqualTo("orders");
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class TopicSummary {
        public int partitions;
        private String name;

        public TopicSummary() { }

        TopicSummary(String name, int partitions) {
            this.name = name;
            this.partitions = partitions;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
