/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.pulsar.shade.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void constructsMapKeyFromJsonPropertyName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Map<TopicKey, Integer> values = mapper.readValue(
                "{\"persistent://orders\":3}", new TypeReference<Map<TopicKey, Integer>>() { });

        assertThat(values).containsEntry(new TopicKey("persistent://orders"), 3);
    }

    public record TopicKey(String value) { }
}
