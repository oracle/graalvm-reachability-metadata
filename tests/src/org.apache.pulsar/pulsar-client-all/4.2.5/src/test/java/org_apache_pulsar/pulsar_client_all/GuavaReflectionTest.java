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

public class GuavaReflectionTest {
    @Test
    void initializesClassesAndCreatesInterfaceProxies() {
        Reflection.initialize(Initialized.class);
        final Greeting greeting = Reflection.newProxy(
                Greeting.class, (proxy, method, arguments) -> "hello " + arguments[0]);

        assertThat(Initialized.VALUE).isEqualTo("initialized");
        assertThat(greeting.greet("pulsar")).isEqualTo("hello pulsar");
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class Initialized {
        private static final String VALUE = initialize();

        private Initialized() {
        }

        private static String initialize() {
            return "initialized";
        }
    }
}
