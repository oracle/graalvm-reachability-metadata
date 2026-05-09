/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.InterceptorMethodHandler;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.context.InvocationContextFactory;
import org.jboss.interceptor.spi.instance.InterceptorInstantiator;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.model.InterceptionModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterceptorMethodHandlerTest {

    @Test
    void invokesNonCandidateMethodOnTargetInstanceForDelegatingProxy() throws Throwable {
        final AtomicInteger targetInstance = new AtomicInteger(7);
        final InterceptorMethodHandler handler = newHandler(targetInstance, AtomicInteger.class);
        final Method equalsMethod = Object.class.getMethod("equals", Object.class);

        final Object result = handler.invoke(
                new AtomicInteger(11),
                equalsMethod,
                equalsMethod,
                new Object[] {targetInstance});

        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    @Test
    void invokesProceedMethodForSubclassProxyNonCandidateMethod() throws Throwable {
        final AtomicInteger proxyInstance = new AtomicInteger(42);
        final InterceptorMethodHandler handler = newHandler(null, AtomicInteger.class);
        final Method thisMethod = Object.class.getMethod("toString");
        final Method proceedMethod = AtomicInteger.class.getMethod("get");

        final Object result = handler.invoke(proxyInstance, thisMethod, proceedMethod, new Object[] {});

        assertThat(result).isEqualTo(42);
    }

    private static <T> InterceptorMethodHandler newHandler(Object targetInstance, Class<T> targetClass) {
        final ClassMetadata<?> targetMetadata = ReflectiveClassMetadata.of(targetClass);
        final InterceptionModel<ClassMetadata<?>, ?> interceptionModel = InterceptionModelBuilder
                .<ClassMetadata<?>, Object>newBuilderFor(targetMetadata, Object.class)
                .build();
        final InterceptorInstantiator<Object, Object> interceptorInstantiator = interceptorReference -> {
            throw new AssertionError("No interceptor instances are required for this non-intercepted method path");
        };
        final InvocationContextFactory invocationContextFactory = null;
        return new InterceptorMethodHandler(
                targetInstance,
                targetMetadata,
                interceptionModel,
                interceptorInstantiator,
                invocationContextFactory);
    }
}
