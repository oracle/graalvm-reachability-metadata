/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventPublicationInterceptor;

import static org.junit.jupiter.api.Assertions.*;

public class EventPublicationInterceptorTest {
    public static class GreetingEvent extends ApplicationEvent {
        public GreetingEvent(Object source) {
            super(source);
        }
    }

    @Test
    void publishesConfiguredEventAfterSuccessfulInvocation() throws Exception {
        List<ApplicationEvent> events = new ArrayList<>();
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        interceptor.setApplicationEventClass(GreetingEvent.class);
        interceptor.setApplicationEventPublisher(events::add);
        interceptor.afterPropertiesSet();
        TestBeans.GreetingService target = new TestBeans.GreetingService();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        TestBeans.Greeting proxy = (TestBeans.Greeting) factory.getProxy();
        assertEquals("Hello listener", proxy.greet("listener"));
        assertEquals(1, events.size());
        assertSame(target, events.getFirst().getSource());
        assertInstanceOf(GreetingEvent.class, events.getFirst());
    }
}
