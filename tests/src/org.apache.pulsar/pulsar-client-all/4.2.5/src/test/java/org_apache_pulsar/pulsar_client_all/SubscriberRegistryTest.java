/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pulsar.shade.com.google.common.eventbus.EventBus;
import org.apache.pulsar.shade.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

public class SubscriberRegistryTest {

    @Test
    void discoversRegistersAndUnregistersSubscriberMethods() {
        final EventBus bus = new EventBus();
        final Counter counter = new Counter();
        bus.register(counter);
        bus.post(4);
        bus.unregister(counter);
        bus.post(9);

        assertThat(counter.total.get()).isEqualTo(4);
    }

    public static final class Counter {
        private final AtomicInteger total = new AtomicInteger();

        @Subscribe
        public void add(Integer value) {
            total.addAndGet(value);
        }
    }
}
