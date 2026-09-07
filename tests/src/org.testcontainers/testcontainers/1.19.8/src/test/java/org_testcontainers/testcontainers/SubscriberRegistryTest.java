/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.eventbus.EventBus;
import org.testcontainers.shaded.com.google.common.eventbus.Subscribe;

import static org.assertj.core.api.Assertions.assertThat;

public class SubscriberRegistryTest {
    @Test
    void discoversAnnotatedSubscriberMethods() {
        EventBus eventBus = new EventBus();
        CountingListener listener = new CountingListener();

        eventBus.register(listener);
        eventBus.post(Integer.valueOf(3));

        assertThat(listener.total).isEqualTo(3);
    }

    public static class CountingListener {
        private int total;

        @Subscribe
        public void add(Integer value) {
            total += value;
        }
    }
}
