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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ReflectionTest {
    @Test
    void initializesClassAndDispatchesInterfaceProxy() {
        Reflection.initialize(InitializedType.class);
        Greeting greeting = Reflection.newProxy(
                Greeting.class,
                (proxy, method, arguments) -> "hello " + arguments[0]);

        assertThat(InitializedType.value()).isEqualTo("initialized");
        assertThat(greeting.greet("pulsar")).isEqualTo("hello pulsar");
    }

    public interface Greeting {
        String greet(String name);
    }

    public static final class InitializedType {
        private static final String VALUE = "initialized";

        private InitializedType() { }

        static String value() {
            return VALUE;
        }
    }
}
