/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotifyCenterTest {
    @Test
    void publishesAnEventToARegisteredSubscriber() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ConfigurationChanged> delivered = new AtomicReference<>();
        Subscriber<ConfigurationChanged> subscriber = new Subscriber<>() {
            @Override
            public void onEvent(ConfigurationChanged event) {
                delivered.set(event);
                received.countDown();
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigurationChanged.class;
            }
        };
        try {
            NotifyCenter.registerSubscriber(subscriber);
            ConfigurationChanged event = new ConfigurationChanged();
            assertTrue(NotifyCenter.publishEvent(event));
            assertTrue(received.await(20, TimeUnit.SECONDS));
            assertSame(event, delivered.get());
        } finally {
            try {
                NotifyCenter.deregisterSubscriber(subscriber);
                NotifyCenter.deregisterPublisher(ConfigurationChanged.class);
            } finally {
                NotifyCenter.shutdown();
            }
        }
    }

    public static class ConfigurationChanged extends Event {
    }
}
