/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.curator.shaded.com.google.common.eventbus.EventBus;
import org.apache.curator.shaded.com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EventBusTest {
    @Test
    void dispatchesEventsToAnnotatedSubscribers() {
        AtomicReference<String> received = new AtomicReference<>();
        EventBus bus = new EventBus();
        bus.register(new Object() {
            @Subscribe
            public void receive(String event) {
                received.set(event);
            }
        });

        bus.post("curator-event");

        assertThat(received).hasValue("curator-event");
    }
}
