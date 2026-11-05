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

public class SubscriberTest {
    @Test
    void deliversOnlyWhileListenerIsRegistered() {
        EventBus bus = new EventBus();
        Listener listener = new Listener();
        bus.register(listener);
        bus.post("first");
        bus.post("second");
        bus.unregister(listener);
        bus.post("ignored");
        assertEquals(List.of("first", "second"), listener.events);
    }

    public static class Listener {
        public final List<String> events = new ArrayList<>();

        @Subscribe
        public void receive(String message) {
            events.add(message);
        }
    }
}
