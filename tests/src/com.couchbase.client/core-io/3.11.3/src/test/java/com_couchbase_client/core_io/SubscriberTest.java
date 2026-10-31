/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.eventbus.EventBus;
import com.couchbase.client.core.deps.com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubscriberTest {
    @Test
    void exercisesPublicApi() throws Exception {
        EventBus bus = new EventBus();
        Listener listener = new Listener();
        bus.register(listener);
        bus.post("invoice-created");
        assertEquals(List.of("invoice-created"), listener.messages);
        bus.unregister(listener);
        bus.post("ignored");
        assertEquals(1, listener.messages.size());
    }

    public static class Listener {
        private final List<String> messages = new ArrayList<>();

        @Subscribe
        public void onMessage(String message) {
            messages.add(message);
        }
    }
}
