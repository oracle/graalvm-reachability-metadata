/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

public class MessageListenerAdapterInnerMethodInvokerTest {
    @Test
    void dispatchesDecodedMessageAndSubscriptionPattern() {
        TextSubscriber subscriber = new TextSubscriber();
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber);
        adapter.afterPropertiesSet();

        adapter.onMessage(new DefaultMessage(bytes("orders.created"), bytes("order-42")), bytes("orders.*"));

        assertEquals("order-42", subscriber.payload);
        assertEquals("orders.*", subscriber.pattern);
        assertEquals(1, subscriber.calls);
    }

    @Test
    void dispatchesRawBodyToCustomSingleArgumentMethod() {
        BinarySubscriber subscriber = new BinarySubscriber();
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receive");
        adapter.setSerializer(null);
        adapter.afterPropertiesSet();
        byte[] body = new byte[] {0, 8, -1, 24};

        adapter.onMessage(new DefaultMessage(bytes("binary"), body), bytes("binary"));

        assertArrayEquals(body, subscriber.body);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static class TextSubscriber {
        private String payload;
        private String pattern;
        private int calls;

        public void handleMessage(String message, String subscription) {
            payload = message;
            pattern = subscription;
            calls++;
        }
    }

    public static class BinarySubscriber {
        private byte[] body;

        public void receive(byte[] message) {
            body = message;
        }
    }
}
