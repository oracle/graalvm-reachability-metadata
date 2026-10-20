/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.Test;

public class MethodUtilsTest {
    @Test
    void invokesCompatibleInstanceStaticAndPrimitiveVarargsMethods() throws Exception {
        InvocationTarget target = new InvocationTarget();

        Object instance = MethodUtils.invokeMethod(
                target,
                "total",
                new Object[] {"items", 2, 3, 4},
                new Class<?>[] {String.class, Integer.class, Integer.class, Integer.class});
        Object staticValue = MethodUtils.invokeStaticMethod(
                InvocationTarget.class,
                "staticTotal",
                new Object[] {"items", 5, 6},
                new Class<?>[] {String.class, Integer.class, Integer.class});
        Object exact = MethodUtils.invokeExactMethod(
                target, "echo", new Object[] {"exact"}, new Class<?>[] {String.class});
        Object exactStatic = MethodUtils.invokeExactStaticMethod(
                InvocationTarget.class, "staticEcho", new Object[] {"value"}, new Class<?>[] {String.class});

        assertThat(instance).isEqualTo("items:9");
        assertThat(staticValue).isEqualTo("items:11");
        assertThat(exact).isEqualTo("exact");
        assertThat(exactStatic).isEqualTo("VALUE");
    }

    @Test
    void searchesInterfacesHierarchiesAndAnnotatedMethods() {
        Method interfaceMethod = MethodUtils.getAccessibleMethod(HiddenGreeter.class, "greet", String.class);
        Method matching = MethodUtils.getMatchingMethod(ChildTarget.class, "describe", Integer.class);
        List<Method> publicAnnotated = MethodUtils.getMethodsListWithAnnotation(
                AnnotatedChild.class, Marker.class, true, false);
        List<Method> allAnnotated = MethodUtils.getMethodsListWithAnnotation(
                AnnotatedChild.class, Marker.class, true, true);

        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Greeter.class);
        assertThat(matching).isNotNull();
        assertThat(matching.getDeclaringClass()).isEqualTo(ChildTarget.class);
        assertThat(publicAnnotated).extracting(Method::getName).contains("visible").doesNotContain("hidden");
        assertThat(allAnnotated).extracting(Method::getName).contains("visible", "hidden");
        assertThat(MethodUtils.getMethodObject(InvocationTarget.class, "echo", String.class)).isNotNull();
    }

    public interface Greeter {
        String greet(String value);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Marker {
    }

    public static class InvocationTarget {
        public String total(String label, int... values) {
            return label + ":" + sum(values);
        }

        public static String staticTotal(String label, int... values) {
            return label + ":" + sum(values);
        }

        public String echo(String value) {
            return value;
        }

        public static String staticEcho(String value) {
            return value.toUpperCase();
        }

        private static int sum(int[] values) {
            int result = 0;
            for (int value : values) {
                result += value;
            }
            return result;
        }
    }

    static class HiddenGreeter implements Greeter {
        @Override
        public String greet(String value) {
            return "hello " + value;
        }
    }

    public static class ParentTarget {
        public String describe(Number value) {
            return "number:" + value;
        }
    }

    public static class ChildTarget extends ParentTarget {
        public String describe(Integer value) {
            return "integer:" + value;
        }
    }

    public static class AnnotatedParent {
        @Marker
        public void visible() {
        }
    }

    public static class AnnotatedChild extends AnnotatedParent {
        @Marker
        private void hidden() {
        }
    }
}
