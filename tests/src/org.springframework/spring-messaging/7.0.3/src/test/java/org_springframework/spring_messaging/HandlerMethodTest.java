/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_messaging;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.handler.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class HandlerMethodTest {

    @Test
    void resolvesNamedHandlerMethod() throws Exception {
        OrderHandler bean = new OrderHandler();
        HandlerMethod handler = new HandlerMethod(bean, "accept", String.class);

        assertSame(bean, handler.getBean());
        assertEquals(OrderHandler.class, handler.getBeanType());
        assertEquals(1, handler.getMethodParameters().length);
        assertEquals(String.class, handler.getMethodParameters()[0].getParameterType());
        assertEquals(String.class, handler.getReturnType().getParameterType());
        assertEquals("OrderHandler#accept[1 args]", handler.getShortLogMessage());

    }

    public static class OrderHandler {

        public String accept(String orderId) {
            return "accepted:" + orderId;
        }
    }
}
