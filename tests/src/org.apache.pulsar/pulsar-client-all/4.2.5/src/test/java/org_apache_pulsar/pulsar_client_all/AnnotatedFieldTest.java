/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotatedFieldTest {
    @Test
    void readsAndWritesRenamedPrivateField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TopicPolicy policy = mapper.readValue("{\"maxConsumers\":8}", TopicPolicy.class);

        String json = mapper.writeValueAsString(policy);

        assertThat(policy.maximumConsumers).isEqualTo(8);
        assertThat(json).isEqualTo("{\"maxConsumers\":8}");
    }

    public static final class TopicPolicy {
        @JsonProperty("maxConsumers")
        private int maximumConsumers;
    }
}
