/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.JavaType;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {

    @Test
    void createsMapKeysWithAStringConstructor() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JavaType type = mapper.getTypeFactory().constructMapType(Map.class, TopicKey.class, Integer.class);
        final Map<TopicKey, Integer> values = mapper.readValue("{\"orders\":3}", type);

        assertThat(values).containsEntry(new TopicKey("orders"), 3);
    }

    public static final class TopicKey {
        private final String value;

        public TopicKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TopicKey && value.equals(((TopicKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
