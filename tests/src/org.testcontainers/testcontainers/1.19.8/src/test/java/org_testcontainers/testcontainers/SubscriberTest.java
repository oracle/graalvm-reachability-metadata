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

public class SubscriberTest {
    @Test
    void invokesTheRegisteredSubscriberMethod() {
        EventBus eventBus = new EventBus();
        Listener listener = new Listener();
        eventBus.register(listener);

        eventBus.post("message");

        assertThat(listener.message).isEqualTo("message");
    }

    public static class Listener {
        private String message;

        @Subscribe
        public void receive(String message) {
            this.message = message;
        }
    }
}
