/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.eventbus.EventBus;
import io.trino.jdbc.$internal.guava.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubscriberRegistryTest {
    @Test
    void discoversInheritedSubscriberMethod() {
        EventBus bus = new EventBus();
        Listener listener = new Listener();
        bus.register(listener);
        bus.post(21);
        bus.post(34L);
        assertEquals(List.of(21, 34L), listener.events);
        bus.unregister(listener);
    }

    public static class BaseListener {
        public final List<Number> events = new ArrayList<>();

        @Subscribe
        public void receive(Number event) {
            events.add(event);
        }
    }

    public static class Listener extends BaseListener {
    }
}
