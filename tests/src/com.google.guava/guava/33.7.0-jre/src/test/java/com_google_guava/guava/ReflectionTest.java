/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.Reflection;
import java.lang.reflect.InvocationHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    private static final AtomicBoolean INITIALIZER_RAN = new AtomicBoolean();

    @Test
    void initializeRunsStaticInitializerForClassLiteral() {
        Reflection.initialize(ClassWithStaticInitializer.class);

        assertThat(INITIALIZER_RAN).isTrue();
    }

    @Test
    void newProxyDispatchesInterfaceCallsToInvocationHandler() {
        AtomicInteger invocations = new AtomicInteger();
        InvocationHandler handler =
                (proxy, method, arguments) -> {
                    invocations.incrementAndGet();
                    return switch (method.getName()) {
                        case "greet" -> "Hello, " + arguments[0];
                        case "invocationCount" -> invocations.get();
                        default -> throw new AssertionError(
                                "Unexpected method: " + method.getName());
                    };
                };

        GreetingService proxy = Reflection.newProxy(GreetingService.class, handler);

        assertThat(proxy.greet("Guava")).isEqualTo("Hello, Guava");
        assertThat(proxy.invocationCount()).isEqualTo(2);
    }

    interface GreetingService {
        String greet(String name);

        int invocationCount();
    }

    private static final class ClassWithStaticInitializer {
        static {
            INITIALIZER_RAN.set(true);
        }
    }
}
