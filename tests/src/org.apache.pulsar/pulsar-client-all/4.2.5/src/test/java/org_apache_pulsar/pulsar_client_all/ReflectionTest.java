/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.reflect.Reflection;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    @Test
    void initializesClassesAndCreatesInterfaceProxy() {
        assertThat(InitializationTracker.initialized).isFalse();

        Reflection.initialize(InitializedComponent.class);
        Greeting greeting = Reflection.newProxy(Greeting.class, (proxy, method, arguments) -> "hello " + arguments[0]);

        assertThat(InitializationTracker.initialized).isTrue();
        assertThat(greeting.greet("Pulsar")).isEqualTo("hello Pulsar");
    }

    public interface Greeting {
        String greet(String name);
    }

    private static final class InitializationTracker {
        private static boolean initialized;
    }

    private static final class InitializedComponent {
        static {
            InitializationTracker.initialized = true;
        }
    }
}
