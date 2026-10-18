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

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {

    @Test
    void createsMapKeysWithAStringFactory() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JavaType type = mapper.getTypeFactory().constructMapType(Map.class, SubscriptionKey.class, Boolean.class);
        final Map<SubscriptionKey, Boolean> values = mapper.readValue("{\"replication\":true}", type);

        assertThat(values).containsEntry(SubscriptionKey.valueOf("replication"), true);
    }

    public static final class SubscriptionKey {
        private final String value;

        private SubscriptionKey(String value) {
            this.value = value;
        }

        public static SubscriptionKey valueOf(String value) {
            return new SubscriptionKey(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SubscriptionKey && value.equals(((SubscriptionKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
