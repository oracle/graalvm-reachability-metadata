/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_curator.curator_framework;

import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.curator.framework.listen.ListenerContainer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListenerContainerTest {
    @Test
    void notifiesOnlyRegisteredListenersAndClearsTheContainer() {
        ListenerContainer<Runnable> listeners = new ListenerContainer<>();
        AtomicInteger firstNotifications = new AtomicInteger();
        AtomicInteger secondNotifications = new AtomicInteger();
        Runnable firstListener = firstNotifications::incrementAndGet;
        Runnable secondListener = secondNotifications::incrementAndGet;

        listeners.addListener(firstListener);
        listeners.addListener(secondListener);
        assertThat(listeners.size()).isEqualTo(2);

        listeners.forEach(ListenerContainerTest::notifyListener);
        assertThat(firstNotifications).hasValue(1);
        assertThat(secondNotifications).hasValue(1);

        listeners.removeListener(firstListener);
        assertThat(listeners.size()).isOne();
        listeners.forEach(ListenerContainerTest::notifyListener);
        assertThat(firstNotifications).hasValue(1);
        assertThat(secondNotifications).hasValue(2);

        listeners.addListener(firstListener);
        assertThat(listeners.size()).isEqualTo(2);
        listeners.forEach(ListenerContainerTest::notifyListener);
        assertThat(firstNotifications).hasValue(2);
        assertThat(secondNotifications).hasValue(3);

        listeners.clear();
        assertThat(listeners.size()).isZero();
        listeners.forEach(ListenerContainerTest::notifyListener);
        assertThat(firstNotifications).hasValue(2);
        assertThat(secondNotifications).hasValue(3);
    }

    private static Void notifyListener(Runnable listener) {
        listener.run();
        return null;
    }
}
