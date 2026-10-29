/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.List;

import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.InterceptorInvocation;
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.reader.InterceptorMetadataUtils;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.DecoratingInterceptor;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.LifecycleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.interceptorMetadata;

public class InterceptorInvocationInnerInterceptorMethodInvocationTest {
    @Test
    void invokesAnAroundInterceptorWithItsInvocationContext() throws Throwable {
        GreetingService target = new GreetingService();
        Method method = GreetingService.class.getMethod("greet", String.class);
        InterceptorInvocation<?> invocation = new InterceptorInvocation<>(
                new DecoratingInterceptor(),
                interceptorMetadata(DecoratingInterceptor.class), InterceptionType.AROUND_INVOKE);
        SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(invocation), InterceptionType.AROUND_INVOKE, target, method);

        Object result = chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, method, new Object[]{"Grace"}));

        assertThat(result).isEqualTo("[Hello, Grace]");
    }

    @Test
    void invokesATargetLifecycleCallbackWithoutArguments() throws Throwable {
        LifecycleService target = new LifecycleService();
        InterceptorInvocation<?> invocation = new InterceptorInvocation<>(target,
                InterceptorMetadataUtils.readMetadataForTargetClass(
                        ReflectiveClassMetadata.of(LifecycleService.class)),
                InterceptionType.PRE_PASSIVATE);
        SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(invocation), InterceptionType.PRE_PASSIVATE, target, null);

        chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, null, new Object[0]));

        assertThat(target.passivations()).isEqualTo(1);
    }
}
