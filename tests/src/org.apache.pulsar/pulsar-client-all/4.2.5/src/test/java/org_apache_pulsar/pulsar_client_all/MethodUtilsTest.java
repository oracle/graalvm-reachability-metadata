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
import java.lang.reflect.Method;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class MethodUtilsTest {
    @Test
    void invokesExactCompatiblePrivateInterfaceAndVarargsMethods() throws Exception {
        Operations operations = new Operations();

        assertThat(MethodUtils.invokeExactMethod(operations, "echo", "client"))
                .isEqualTo("client");
        assertThat(MethodUtils.invokeExactStaticMethod(
                        Operations.class, "identity", "static"))
                .isEqualTo("static");
        assertThat(MethodUtils.invokeMethod(operations, "describe", Integer.valueOf(4)))
                .isEqualTo("number:4");
        assertThat(MethodUtils.invokeMethod(operations, true, "hidden", "metadata"))
                .isEqualTo("hidden:metadata");
        assertThat(MethodUtils.invokeMethod(new GreetingImpl(), "greet", "Pulsar"))
                .isEqualTo("Hello Pulsar");
        assertThat(MethodUtils.invokeStaticMethod(Operations.class, "sum", 1, 2, 3))
                .isEqualTo(6);

        assertThat(MethodUtils.getMethodObject(Operations.class, "echo", String.class))
                .isNotNull();
        assertThat(MethodUtils.getMatchingMethod(Operations.class, "describe", Integer.class))
                .isNotNull();

        List<Method> declared = MethodUtils.getMethodsListWithAnnotation(
                Operations.class, Exercised.class, false, true);
        List<Method> accessible = MethodUtils.getMethodsListWithAnnotation(
                Operations.class, Exercised.class, false, false);
        assertThat(declared).extracting(Method::getName).contains("echo", "hidden");
        assertThat(accessible).extracting(Method::getName).contains("echo");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Exercised { }

    public interface Greeting {
        String greet(String name);
    }

    private static final class GreetingImpl implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static final class Operations {
        @Exercised
        public String echo(String value) {
            return value;
        }

        public String describe(Number value) {
            return "number:" + value;
        }

        @Exercised
        private String hidden(String value) {
            return "hidden:" + value;
        }

        public static String identity(String value) {
            return value;
        }

        public static int sum(int... values) {
            int result = 0;
            for (int value : values) {
                result += value;
            }
            return result;
        }
    }
}
