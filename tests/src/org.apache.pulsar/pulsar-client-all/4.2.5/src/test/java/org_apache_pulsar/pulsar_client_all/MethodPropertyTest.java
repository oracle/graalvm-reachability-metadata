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
    void deserializesJsonThroughBeanSetters() throws Exception {
        final SetterMessage message = new ObjectMapper().readValue(
                "{\"topic\":\"events\",\"replicated\":true}", SetterMessage.class);

        assertThat(message.getTopic()).isEqualTo("events");
        assertThat(message.isReplicated()).isTrue();
    }

    public static final class SetterMessage {
        private String topic;
        private boolean replicated;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public boolean isReplicated() {
            return replicated;
        }

        public void setReplicated(boolean replicated) {
            this.replicated = replicated;
        }
    }
}
