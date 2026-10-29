/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class NotifyCenterTest {
    public static class ConfigurationEvent extends Event {
    }

    @Test
    void dispatchesEventsToRegisteredSubscribers() throws Exception {
        BlockingQueue<Event> received = new ArrayBlockingQueue<>(1);
        Subscriber<ConfigurationEvent> subscriber = new Subscriber<ConfigurationEvent>() {
            @Override
            public void onEvent(ConfigurationEvent event) {
                received.add(event);
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigurationEvent.class;
            }
        };
        NotifyCenter.registerSubscriber(subscriber);
        try {
            ConfigurationEvent event = new ConfigurationEvent();
            assertTrue(NotifyCenter.publishEvent(event));
            assertEquals(event, received.poll(30, TimeUnit.SECONDS));
        } finally {
            NotifyCenter.deregisterSubscriber(subscriber);
            NotifyCenter.deregisterPublisher(ConfigurationEvent.class);
        }
    }
}
