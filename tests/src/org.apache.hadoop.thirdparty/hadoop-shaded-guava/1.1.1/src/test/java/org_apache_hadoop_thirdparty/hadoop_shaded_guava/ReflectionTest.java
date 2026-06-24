/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.thirdparty.com.google.common.reflect.Reflection;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Test
    void initializeRunsStaticInitializersForClassLiterals() {
        assertThat(INITIALIZED).isFalse();

        Reflection.initialize(InitializedByReflection.class);

        assertThat(INITIALIZED).isTrue();
    }

    @Test
    void newProxyCreatesInterfaceProxyBackedByInvocationHandler() {
        AtomicReference<Method> invokedMethod = new AtomicReference<>();

        GreetingService proxy = Reflection.newProxy(
                GreetingService.class,
                (target, method, arguments) -> {
                    invokedMethod.set(method);
                    return "Hello, " + arguments[0];
                });

        assertThat(proxy.greet("Hadoop")).isEqualTo("Hello, Hadoop");
        assertThat(invokedMethod.get().getName()).isEqualTo("greet");
    }

    interface GreetingService {
        String greet(String name);
    }

    private static final class InitializedByReflection {
        static {
            INITIALIZED.set(true);
        }

        private InitializedByReflection() {
        }
    }
}
