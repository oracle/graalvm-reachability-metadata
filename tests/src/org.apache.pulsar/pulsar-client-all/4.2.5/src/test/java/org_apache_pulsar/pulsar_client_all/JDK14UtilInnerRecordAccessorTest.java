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

public class JDK14UtilInnerRecordAccessorTest {
    @Test
    void discoversAndUsesRecordComponents() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Topic record = mapper.readValue("{\"name\":\"events\",\"partitions\":4}", Topic.class);

        assertThat(record).isEqualTo(new Topic("events", 4));
        assertThat(mapper.writeValueAsString(record)).contains("\"name\":\"events\"", "\"partitions\":4");
    }

    public record Topic(String name, int partitions) {
    }
}
