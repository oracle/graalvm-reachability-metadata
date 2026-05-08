/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_containers.jersey_container_servlet_core;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.glassfish.jersey.servlet.internal.ThreadLocalInvoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalInvokerTest {
    @Test
    void delegatesInvocationToThreadLocalInstance() throws Throwable {
        ThreadLocalInvoker<Function<String, String>> invoker = new ThreadLocalInvoker<>();
        invoker.set(name -> "Hello, " + name);
        Method applyMethod = Function.class.getMethod("apply", Object.class);

        Object result = invoker.invoke(new Object(), applyMethod, new Object[] {"Jersey"});

        assertThat(result).isEqualTo("Hello, Jersey");
    }
}
