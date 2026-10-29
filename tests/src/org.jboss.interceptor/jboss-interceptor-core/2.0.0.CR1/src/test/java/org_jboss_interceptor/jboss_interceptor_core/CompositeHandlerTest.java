/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.List;

import org.jboss.interceptor.proxy.javassist.CompositeHandler;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeHandlerTest {
    @Test
    void proceedsToTheBusinessMethodWhenNoHandlersAreRegistered() throws Throwable {
        GreetingService target = new GreetingService();
        Method method = GreetingService.class.getMethod("greet", String.class);
        CompositeHandler handler = new CompositeHandler(List.of());

        assertThat(handler.invoke(target, method, method, new Object[]{"Sam"})).isEqualTo("Hello, Sam");
        assertThat(handler.invoke(target, method, method, new Object[]{"Pat"})).isEqualTo("Hello, Pat");
    }

    @Test
    void objectMethodsBypassTheInterceptorList() throws Throwable {
        GreetingService target = new GreetingService();
        Method method = Object.class.getMethod("toString");
        CompositeHandler handler = new CompositeHandler(List.of(
                (self, called, proceed, args) -> "intercepted"));

        assertThat(handler.invoke(target, method, method, new Object[0])).isEqualTo("greeting service");
    }
}
