/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class SetterlessPropertyTest {
    @Test
    void populatesMutableCollectionReturnedByGetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConsumerGroup group = mapper.readValue(
                "{\"consumers\":[\"consumer-a\",\"consumer-b\"]}", ConsumerGroup.class);

        assertThat(group.getConsumers()).containsExactly("consumer-a", "consumer-b");
    }

    public static final class ConsumerGroup {
        private final List<String> consumers = new ArrayList<>();

        public List<String> getConsumers() {
            return consumers;
        }
    }
}
