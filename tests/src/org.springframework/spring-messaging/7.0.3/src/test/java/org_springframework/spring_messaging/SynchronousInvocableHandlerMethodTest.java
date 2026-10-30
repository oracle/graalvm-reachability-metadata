/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.messaging.support.GenericMessage;
import org_springframework.spring_messaging.HandlerMethodTest.OrderHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SynchronousInvocableHandlerMethodTest {

    @Test
    void invokesNamedHandlerWithMessagePayload() throws Exception {
        OrderHandler bean = new OrderHandler();
        HandlerMethod handler = new HandlerMethod(bean, "accept", String.class);
        GenericMessage<String> message = new GenericMessage<>("order-42");
        InvocableHandlerMethod invocable = new InvocableHandlerMethod(handler);

        assertEquals("accepted:order-42", invocable.invoke(message, message.getPayload()));
    }
}
