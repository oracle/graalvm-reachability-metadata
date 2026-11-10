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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodUtilsTest {
    @Test
    void invokesExactAssignableAndVariableArgumentMethods() throws Exception {
        Message message = new Message();
        assertThat(MethodUtils.invokeExactMethod(message, "greet", "reader")).isEqualTo("hello reader");
        assertThat(MethodUtils.invokeMethod(message, "describe", Integer.valueOf(7))).isEqualTo("value=7");
        assertThat(MethodUtils.invokeMethod(message, true, "secret", "reader")).isEqualTo("private reader");
        assertThat(MethodUtils.invokeExactStaticMethod(Message.class, "uppercase", "reader")).isEqualTo("READER");
        assertThat(MethodUtils.invokeStaticMethod(Message.class, "join", "a", "b", "c")).isEqualTo("a/b/c");
    }

    @Test
    void resolvesPublicInterfaceAndSuperclassMethodsOnHiddenImplementations() throws Exception {
        assertThat(MethodUtils.invokeExactMethod(new HiddenGreeting(), "greet", "reader"))
                .isEqualTo("interface reader");
        assertThat(MethodUtils.invokeExactMethod(new HiddenMessage(), "greet", "reader"))
                .isEqualTo("hello reader");
    }

    @Test
    void findsAnnotatedPublicAndPrivateMethods() {
        assertThat(MethodUtils.getMethodsListWithAnnotation(Message.class, Marker.class))
                .extracting(method -> method.getName()).containsExactly("greet");
        assertThat(MethodUtils.getMethodsListWithAnnotation(Message.class, Marker.class, false, true))
                .extracting(method -> method.getName()).containsExactlyInAnyOrder("greet", "secret");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
    }

    public interface Greeting {
        String greet(String name);
    }

    private static class HiddenGreeting implements Greeting {
        @Override
        public String greet(String name) {
            return "interface " + name;
        }
    }

    private static class HiddenMessage extends Message {
    }

    public static class Message {
        @Marker
        public String greet(String name) {
            return "hello " + name;
        }

        public String describe(Number value) {
            return "value=" + value;
        }

        @Marker
        private String secret(String name) {
            return "private " + name;
        }

        public static String uppercase(String name) {
            return name.toUpperCase(Locale.ROOT);
        }

        public static String join(String... parts) {
            return String.join("/", parts);
        }
    }
}
