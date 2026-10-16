/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.Test;

public class MethodUtilsTest {
    @Test
    void discoversAndInvokesInstanceStaticAnnotatedAndVarargsMethods() throws Exception {
        final MethodFixture fixture = new MethodFixture();

        assertThat(MethodUtils.invokeExactMethod(fixture, "echo", "pulsar")).isEqualTo("pulsar");
        assertThat(MethodUtils.invokeMethod(fixture, "repeat", "x", 3)).isEqualTo("xxx");
        assertThat(MethodUtils.invokeMethod(fixture, "sum", 2, 3, 4)).isEqualTo(9);
        assertThat(MethodUtils.invokeExactStaticMethod(MethodFixture.class, "identity", "static"))
                .isEqualTo("static");
        assertThat(MethodUtils.invokeStaticMethod(MethodFixture.class, "identity", "flexible"))
                .isEqualTo("flexible");
        assertThat(MethodUtils.getMatchingMethod(MethodFixture.class, "repeat", String.class, Integer.class))
                .isNotNull();
        assertThat(MethodUtils.getMatchingAccessibleMethod(MethodFixture.class, "echo", String.class)).isNotNull();
        assertThat(MethodUtils.getMethodObject(MethodFixture.class, "echo", String.class)).isNotNull();
        assertThat(MethodUtils.getMethodsListWithAnnotation(MethodFixture.class, Callable.class))
                .extracting(method -> method.getName())
                .containsExactly("echo");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Callable {
    }

    public static final class MethodFixture {
        @Callable
        public String echo(String value) {
            return value;
        }

        public String repeat(CharSequence value, int count) {
            return value.toString().repeat(count);
        }

        public int sum(int... values) {
            int total = 0;
            for (int value : values) {
                total += value;
            }
            return total;
        }

        public static String identity(String value) {
            return value;
        }
    }
}
