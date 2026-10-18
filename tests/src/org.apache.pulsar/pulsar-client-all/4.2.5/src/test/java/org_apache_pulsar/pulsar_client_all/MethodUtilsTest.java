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
    void invokesInstanceStaticExactAndVarargsMethods() throws Exception {
        final MethodTarget target = new MethodTarget("prefix");

        assertThat(MethodUtils.invokeMethod(target, "join", "a", "b")).isEqualTo("prefix:a,b");
        assertThat(MethodUtils.invokeExactMethod(target, "identity", new Object[] {"value"},
                        new Class<?>[] {String.class}))
                .isEqualTo("value");
        assertThat(MethodUtils.invokeStaticMethod(MethodTarget.class, "sum", 2, 5)).isEqualTo(7);
        assertThat(MethodUtils.invokeExactStaticMethod(MethodTarget.class, "label")).isEqualTo("target");
        assertThat(MethodUtils.getMatchingMethod(MethodTarget.class, "identity", String.class)).isNotNull();
        assertThat(MethodUtils.getMethodsListWithAnnotation(MethodTarget.class, Marker.class))
                .extracting(method -> method.getName())
                .containsExactly("identity");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
    }

    public static final class MethodTarget {
        private final String prefix;

        public MethodTarget(String prefix) {
            this.prefix = prefix;
        }

        public String join(String... values) {
            return prefix + ":" + String.join(",", values);
        }

        @Marker
        public String identity(String value) {
            return value;
        }

        public static int sum(Integer left, Integer right) {
            return left + right;
        }

        public static String label() {
            return "target";
        }
    }
}
