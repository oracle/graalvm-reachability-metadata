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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class MethodPropertyTest {
    @Test
    void invokesSettersWhileUpdatingBean() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ConsumerSettings settings = new ConsumerSettings();

        ConsumerSettings updated = mapper.readerForUpdating(settings)
                .readValue("{\"subscription\":\"durable-sub\",\"priority\":7}");

        assertThat(updated).isSameAs(settings);
        assertThat(updated.getSubscription()).isEqualTo("durable-sub");
        assertThat(updated.getPriority()).isEqualTo(7);
    }

    public static final class ConsumerSettings {
        private String subscription;
        private int priority;

        public String getSubscription() {
            return subscription;
        }

        public void setSubscription(String subscription) {
            this.subscription = subscription;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }
}
