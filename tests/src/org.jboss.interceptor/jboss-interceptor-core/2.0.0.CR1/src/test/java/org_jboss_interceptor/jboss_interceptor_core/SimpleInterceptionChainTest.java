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
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.LifecycleService;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleInterceptionChainTest {
    @Test
    void passesBusinessArgumentsToTheTarget() throws Throwable {
        GreetingService target = new GreetingService();
        Method method = GreetingService.class.getMethod("greet", String.class);
        SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(), InterceptionType.AROUND_INVOKE, target, method);

        assertThat(chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, method, new Object[]{"Mira"})))
                .isEqualTo("Hello, Mira");
        assertThat(chain.hasNextInterceptor()).isFalse();
    }

    @Test
    void invokesANoArgumentLifecycleTarget() throws Throwable {
        LifecycleService target = new LifecycleService();
        Method method = LifecycleService.class.getMethod("passivate");
        SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(), InterceptionType.PRE_PASSIVATE, target, method);

        assertThat(chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, null, new Object[0]))).isNull();
        assertThat(target.passivations()).isEqualTo(1);
    }
}
