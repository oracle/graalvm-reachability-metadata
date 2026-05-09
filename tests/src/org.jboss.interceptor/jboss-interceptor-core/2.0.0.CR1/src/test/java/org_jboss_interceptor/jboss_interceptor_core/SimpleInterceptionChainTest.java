/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.Collections;

import org.jboss.interceptor.proxy.InterceptorInvocationContext;
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInterceptionChainTest {

    @Test
    void invokesTargetMethodWithInvocationContextParametersWhenChainIsExhausted() throws Throwable {
        final SampleTarget target = new SampleTarget();
        final Method targetMethod = SampleTarget.class.getMethod("join", String.class, String.class);
        final SimpleInterceptionChain chain = new SimpleInterceptionChain(
                Collections.emptyList(),
                InterceptionType.AROUND_INVOKE,
                target,
                targetMethod);
        final InterceptorInvocationContext context = new InterceptorInvocationContext(
                null,
                target,
                targetMethod,
                new Object[] {"alpha", "beta"});

        final Object result = chain.invokeNextInterceptor(context);

        assertThat(result).isEqualTo("alpha:beta");
        assertThat(target.getInvocationCount()).isOne();
    }

    @Test
    void invokesNoArgumentTargetMethodForLifecycleInvocationWhenChainIsExhausted() throws Throwable {
        final SampleTarget target = new SampleTarget();
        final Method targetMethod = SampleTarget.class.getMethod("initialize");
        final SimpleInterceptionChain chain = new SimpleInterceptionChain(
                Collections.emptyList(),
                InterceptionType.POST_CONSTRUCT,
                target,
                targetMethod);
        final InterceptorInvocationContext context = new InterceptorInvocationContext(
                null,
                target,
                null,
                new Object[0]);

        final Object result = chain.invokeNextInterceptor(context);

        assertThat(result).isEqualTo("initialized");
        assertThat(target.getInvocationCount()).isOne();
    }

    public static class SampleTarget {

        private int invocationCount;

        public String join(String left, String right) {
            invocationCount++;
            return left + ":" + right;
        }

        public String initialize() {
            invocationCount++;
            return "initialized";
        }

        int getInvocationCount() {
            return invocationCount;
        }
    }
}
