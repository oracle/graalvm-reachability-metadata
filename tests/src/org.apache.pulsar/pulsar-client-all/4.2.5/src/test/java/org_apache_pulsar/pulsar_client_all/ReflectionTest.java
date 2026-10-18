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
    void initializesClassesAndCreatesTypedProxy() {
        Reflection.initialize(ProxyTarget.class);
        final Greeter greeter = Reflection.newProxy(
                Greeter.class,
                (proxy, method, arguments) -> "hello " + arguments[0]);

        assertThat(ProxyTarget.initialized).isTrue();
        assertThat(greeter.greet("pulsar")).isEqualTo("hello pulsar");
        assertThat(Reflection.getPackageName(Greeter.class))
                .isEqualTo("org_apache_pulsar.pulsar_client_all");
    }

    public interface Greeter {
        String greet(String name);
    }

    public static final class ProxyTarget {
        private static final boolean initialized = initialize();

        private static boolean initialize() {
            return true;
        }
    }
}
