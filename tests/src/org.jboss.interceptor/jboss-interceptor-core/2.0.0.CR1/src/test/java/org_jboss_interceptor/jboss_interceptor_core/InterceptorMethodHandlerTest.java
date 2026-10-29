/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;

import org.jboss.interceptor.proxy.InterceptorMethodHandler;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.handler;

public class InterceptorMethodHandlerTest {
    @Test
    void dispatchesBusinessMethodsThroughTheRegisteredInterceptor() throws Throwable {
        GreetingService target = new GreetingService();
        InterceptorMethodHandler methodHandler = handler(target, GreetingService.class);
        Method method = GreetingService.class.getMethod("greet", String.class);

        assertThat(methodHandler.invoke(null, method, method, new Object[]{"Lin"}))
                .isEqualTo("[Hello, Lin]");
    }

    @Test
    void invokesObjectMethodsOnTheWrappedTarget() throws Throwable {
        GreetingService target = new GreetingService();
        InterceptorMethodHandler methodHandler = handler(target, GreetingService.class);
        Method method = Object.class.getMethod("toString");

        assertThat(methodHandler.invoke(null, method, method, new Object[0])).isEqualTo("greeting service");
    }

    @Test
    void invokesTheProceedMethodForAnUnwrappedInstance() throws Throwable {
        GreetingService self = new GreetingService();
        InterceptorMethodHandler methodHandler = handler(null, GreetingService.class);
        Method method = Object.class.getMethod("toString");
        Method proceed = GreetingService.class.getMethod("description");

        assertThat(methodHandler.invoke(self, method, proceed, new Object[0])).isEqualTo("greeting service");
    }
}
