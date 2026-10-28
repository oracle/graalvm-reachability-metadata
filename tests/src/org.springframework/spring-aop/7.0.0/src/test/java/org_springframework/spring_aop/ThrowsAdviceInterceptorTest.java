/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.adapter.ThrowsAdviceInterceptor;

public class ThrowsAdviceInterceptorTest {

    @Test
    void invokesMatchingThrowsAdviceHandlerWhenInvocationThrows() throws Throwable {
        IllegalStateException failure = new IllegalStateException("expected failure");
        RecordingThrowsAdvice advice = new RecordingThrowsAdvice();
        ThrowsAdviceInterceptor interceptor = new ThrowsAdviceInterceptor(advice);

        Throwable thrown = catchThrowable(() -> interceptor.invoke(new FailingMethodInvocation(failure)));

        assertThat(thrown).isSameAs(failure);
        assertThat(interceptor.getHandlerMethodCount()).isEqualTo(1);
        assertThat(advice.handledException()).isSameAs(failure);
    }

    public static class RecordingThrowsAdvice implements ThrowsAdvice {
        private IllegalStateException handledException;

        public void afterThrowing(IllegalStateException ex) {
            this.handledException = ex;
        }

        IllegalStateException handledException() {
            return this.handledException;
        }
    }

    private static class FailingMethodInvocation implements MethodInvocation {
        private final Throwable failure;

        FailingMethodInvocation(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public Method getMethod() {
            throw new UnsupportedOperationException("Single-argument throws advice does not need the invoked method");
        }

        @Override
        public Object[] getArguments() {
            throw new UnsupportedOperationException("Single-argument throws advice does not need invocation arguments");
        }

        @Override
        public Object proceed() throws Throwable {
            throw this.failure;
        }

        @Override
        public Object getThis() {
            throw new UnsupportedOperationException("Single-argument throws advice does not need the invocation target");
        }

        @Override
        public AccessibleObject getStaticPart() {
            throw new UnsupportedOperationException("Single-argument throws advice does not need the static part");
        }
    }
}
