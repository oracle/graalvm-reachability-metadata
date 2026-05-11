/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_containers.jersey_container_servlet;

import java.lang.reflect.Proxy;
import java.util.function.Function;

import org.glassfish.jersey.servlet.internal.ThreadLocalInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalInvokerTest {
    @Test
    void proxyDelegatesMethodInvocationToThreadLocalInstance() {
        ThreadLocalInvoker<Function<String, String>> invoker = new ThreadLocalInvoker<>();
        invoker.set(name -> "Hello, " + name);

        Function<String, String> proxy = createFunctionProxy(invoker);

        assertThat(proxy.apply("Jersey")).isEqualTo("Hello, Jersey");
    }

    @SuppressWarnings("unchecked")
    private static Function<String, String> createFunctionProxy(
            final ThreadLocalInvoker<Function<String, String>> invoker) {
        return (Function<String, String>) Proxy.newProxyInstance(
                ThreadLocalInvokerTest.class.getClassLoader(),
                new Class<?>[] { Function.class },
                invoker);
    }
}
