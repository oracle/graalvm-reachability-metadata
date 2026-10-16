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

public class MethodPropertyTest {
    @Test
    void deserializesThroughBeanSetters() throws Exception {
        final SetterConfiguration configuration = new ObjectMapper().readValue(
                "{\"topic\":\"persistent://tenant/ns/events\"}", SetterConfiguration.class);

        assertThat(configuration.getTopic()).isEqualTo("persistent://tenant/ns/events");
    }

    public static final class SetterConfiguration {
        private String topic;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String configuredTopic) {
            topic = configuredTopic;
        }
    }
}
