/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.event.EventListenerSupport;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EventListenerSupportInnerProxyInvocationHandlerTest {
    @Test
    void proxyInvokesEveryRegisteredListener() {
        EventListenerSupport<CounterListener> support = EventListenerSupport.create(CounterListener.class);
        AtomicInteger total = new AtomicInteger();
        support.addListener(total::incrementAndGet);
        support.addListener(total::incrementAndGet);

        support.fire().increment();

        assertThat(total).hasValue(2);
    }

    public interface CounterListener {
        void increment();
    }
}
