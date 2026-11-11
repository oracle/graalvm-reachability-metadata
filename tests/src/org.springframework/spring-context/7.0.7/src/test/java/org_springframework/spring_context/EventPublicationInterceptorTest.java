/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventPublicationInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class EventPublicationInterceptorTest {
    @Test
    void publishesConfiguredEventAfterSuccessfulServiceInvocation() throws Exception {
        List<Object> published = new ArrayList<>();
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        interceptor.setApplicationEventClass(Completed.class);
        interceptor.setApplicationEventPublisher(published::add);
        interceptor.afterPropertiesSet();
        OrderService target = new OrderService();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(Orders.class);
        factory.addAdvice(interceptor);
        Orders orders = (Orders) factory.getProxy();
        assertEquals("accepted", orders.submit());
        assertEquals(1, published.size());
        Completed event = assertInstanceOf(Completed.class, published.getFirst());
        assertSame(target, event.getSource());
    }

    public interface Orders {
        String submit();
    }

    public static class OrderService implements Orders {
        @Override
        public String submit() {
            return "accepted";
        }
    }

    public static class Completed extends ApplicationEvent {
        public Completed(Object source) {
            super(source);
        }
    }
}
