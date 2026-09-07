/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodUtilsTest {
    @Test
    void discoversAndInvokesCompatibleMethods() throws Exception {
        HiddenGreeter greeter = new HiddenGreeter();

        assertThat(MethodUtils.invokeMethod(greeter, "greet", "Ada")).isEqualTo("Hello Ada");
        assertThat(MethodUtils.invokeMethod(greeter, "join", "a", "b")).isEqualTo("a:b");
        assertThat(MethodUtils.invokeExactMethod(new PublicMethods(), "echo", "exact")).isEqualTo("exact");
        assertThat(MethodUtils.invokeStaticMethod(PublicMethods.class, "number", Integer.valueOf(4))).isEqualTo(4);
        assertThat(MethodUtils.invokeExactStaticMethod(PublicMethods.class, "number", Integer.valueOf(5))).isEqualTo(5);
        assertThat(MethodUtils.invokeStaticMethod(PublicMethods.class, "number", Long.valueOf(6))).isEqualTo(6L);
        assertThat(MethodUtils.getAccessibleMethod(HiddenGreeter.class, "greet", String.class)).isNotNull();
        assertThat(MethodUtils.getAccessibleMethod(HiddenPublicMethods.class, "echo", String.class)).isNotNull();
        assertThat(MethodUtils.getMatchingAccessibleMethod(PublicMethods.class, "number", Long.class)).isNotNull();
        assertThat(MethodUtils.getMatchingMethod(PublicMethods.class, "number", Integer.class)).isNotNull();
        assertThat(MethodUtils.getMethodsListWithAnnotation(PublicMethods.class, Marker.class)).hasSize(1);
        assertThat(MethodUtils.getMethodsListWithAnnotation(PublicMethods.class, Marker.class, true, true)).hasSize(1);
    }

    public interface Greeter {
        String greet(String name);
    }

    private static final class HiddenGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }

        public String join(String... values) {
            return String.join(":", values);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {}

    public static class PublicMethods {
        public String echo(String value) {
            return value;
        }

        public static Number number(Number value) {
            return value;
        }

        public static Integer number(Integer value) {
            return value;
        }

        @Marker
        public void marked() {}
    }

    private static class HiddenPublicMethods extends PublicMethods {
        @Override
        public String echo(String value) {
            return value;
        }
    }
}
