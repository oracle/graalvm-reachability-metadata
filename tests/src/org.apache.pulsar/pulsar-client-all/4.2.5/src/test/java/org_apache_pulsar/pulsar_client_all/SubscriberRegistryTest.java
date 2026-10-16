/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.eventbus.EventBus;
import org.apache.pulsar.shade.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

public class SubscriberRegistryTest {
    @Test
    void discoversAnnotatedSubscriberMethods() {
        final EventBus eventBus = new EventBus();
        final CountingListener listener = new CountingListener();
        eventBus.register(listener);

        eventBus.post(2);
        eventBus.post(3);

        assertThat(listener.total).isEqualTo(5);
    }

    public static final class CountingListener {
        private int total;

        @Subscribe
        public void receive(Integer value) {
            total += value;
        }
    }
}
