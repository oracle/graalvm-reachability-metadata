/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventPublicationInterceptor;

public class EventPublicationInterceptorTest {

    @Test
    void publishesConfiguredApplicationEventAfterSuccessfulInvocation() throws Throwable {
        EventPublicationInterceptor interceptor = new EventPublicationInterceptor();
        AtomicReference<Object> publishedEvent = new AtomicReference<>();
        Object invocationTarget = new Object();
        Object returnValue = new Object();

        interceptor.setApplicationEventClass(PublicationTestEvent.class);
        interceptor.setApplicationEventPublisher(publishedEvent::set);
        interceptor.afterPropertiesSet();

        Object actualReturnValue = interceptor.invoke(new ProceedingInvocation(invocationTarget, returnValue));

        assertSame(returnValue, actualReturnValue);
        PublicationTestEvent event = assertInstanceOf(PublicationTestEvent.class, publishedEvent.get());
        assertSame(invocationTarget, event.getSource());
        assertEquals(1, event.constructorCallCount());
    }

    public static class PublicationTestEvent extends ApplicationEvent {

        private int constructorCallCount;

        public PublicationTestEvent(Object source) {
            super(source);
            this.constructorCallCount++;
        }

        int constructorCallCount() {
            return this.constructorCallCount;
        }
    }

    private static class ProceedingInvocation implements MethodInvocation {

        private final Object invocationTarget;

        private final Object returnValue;

        ProceedingInvocation(Object invocationTarget, Object returnValue) {
            this.invocationTarget = invocationTarget;
            this.returnValue = returnValue;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }

        @Override
        public Object proceed() {
            return this.returnValue;
        }

        @Override
        public Object getThis() {
            return this.invocationTarget;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return null;
        }
    }
}
