/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.invocation.reactive.InvocableHandlerMethod;
import org.springframework.messaging.support.GenericMessage;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InvocableHandlerMethodTest {

    @Test
    void invokesHandlerWithProvidedPayloadAndEmitsItsReturnValue() throws Exception {
        OrderHandler bean = new OrderHandler();
        HandlerMethod handler = new HandlerMethod(bean, "accept", String.class);
        InvocableHandlerMethod invocable = new InvocableHandlerMethod(handler);
        GenericMessage<String> message = new GenericMessage<>("order-57");

        Object result = invocable.invoke(message, message.getPayload()).block(Duration.ofSeconds(10));

        assertEquals("accepted:order-57", result);
    }

    @Test
    void subscribesToVoidPublisherReturnedByHandlerBeforeCompleting() throws Exception {
        OrderHandler bean = new OrderHandler();
        HandlerMethod handler = new HandlerMethod(bean, "acknowledge", String.class);
        InvocableHandlerMethod invocable = new InvocableHandlerMethod(handler);
        GenericMessage<String> message = new GenericMessage<>("order-86");

        Mono<Object> invocation = invocable.invoke(message, message.getPayload());

        assertNull(bean.acknowledgedOrder);
        assertNull(invocation.block(Duration.ofSeconds(10)));
        assertEquals("order-86", bean.acknowledgedOrder);
    }

    public static class OrderHandler {

        private String acknowledgedOrder;

        public String accept(String orderId) {
            return "accepted:" + orderId;
        }

        public Mono<Void> acknowledge(String orderId) {
            return Mono.fromRunnable(() -> acknowledgedOrder = orderId);
        }
    }
}
