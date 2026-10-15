/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.apache.pulsar.shade.com.fasterxml.jackson.core.type.TypeReference;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void invokesStringFactoryForJsonPropertyName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Map<SubscriptionKey, Boolean> values = mapper.readValue(
                "{\"orders-sub\":true}",
                new TypeReference<Map<SubscriptionKey, Boolean>>() { });

        assertThat(values).containsEntry(SubscriptionKey.valueOf("orders-sub"), true);
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
            return other instanceof SubscriptionKey
                    && Objects.equals(value, ((SubscriptionKey) other).value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
