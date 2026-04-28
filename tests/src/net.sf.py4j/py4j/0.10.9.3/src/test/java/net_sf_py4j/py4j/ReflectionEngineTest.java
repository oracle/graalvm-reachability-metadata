/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import org.junit.jupiter.api.Test;
import py4j.reflection.MethodInvoker;
import py4j.reflection.ReflectionEngine;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionEngineTest {
    private final ReflectionEngine reflectionEngine = new ReflectionEngine();

    @Test
    void createsTypedArrays() {
        String[][] matrix = (String[][]) reflectionEngine.createArray(String.class.getName(), new int[] { 2, 3 });
        assertThat(matrix.length).isEqualTo(2);
        assertThat(matrix[0].length).isEqualTo(3);

        int[] values = (int[]) reflectionEngine.createArray("int", new int[] { 4 });
        assertThat(values).hasSize(4);
    }

    @Test
    void resolvesConstructorsAndMethods() {
        MethodInvoker constructor = reflectionEngine.getConstructor(
                ReflectiveFixture.class,
                new Class<?>[] { String.class });
        ReflectiveFixture fixture = (ReflectiveFixture) reflectionEngine.invoke(
                null,
                constructor,
                new Object[] { "custom" });

        assertThat(fixture.greet()).isEqualTo("custom world");
        assertThat(reflectionEngine.getMethod(ReflectiveFixture.class, "greet").getName()).isEqualTo("greet");

        MethodInvoker join = reflectionEngine.getMethod(
                ReflectiveFixture.class,
                "join",
                new Class<?>[] { String.class, Integer.class });
        assertThat(reflectionEngine.invoke(fixture, join, new Object[] { "item", 2 })).isEqualTo("custom:item:2");
    }

    @Test
    void readsAndWritesPublicFields() {
        ReflectiveFixture fixture = new ReflectiveFixture();
        Field field = reflectionEngine.getField(fixture, "mutableNumber");

        assertThat(field).isNotNull();
        assertThat(reflectionEngine.getFieldValue(fixture, field)).isEqualTo(7);

        reflectionEngine.setFieldValue(fixture, field, 42);
        assertThat(fixture.mutableNumber).isEqualTo(42);
        assertThat(reflectionEngine.getFieldValue(fixture, field)).isEqualTo(42);
    }

    @Test
    void listsPublicMembers() {
        ReflectiveFixture fixture = new ReflectiveFixture();

        assertThat(reflectionEngine.getClass(ReflectiveFixture.class, "PublicNested"))
                .isEqualTo(ReflectiveFixture.PublicNested.class);
        assertThat(reflectionEngine.getPublicMethodNames(fixture)).contains("greet", "join");
        assertThat(reflectionEngine.getPublicFieldNames(fixture)).contains("mutableNumber", "staticMutableText");
        assertThat(reflectionEngine.getPublicStaticFieldNames(ReflectiveFixture.class)).contains("staticMutableText");
        assertThat(reflectionEngine.getPublicStaticMethodNames(ReflectiveFixture.class)).contains("staticGreeting");
        assertThat(reflectionEngine.getPublicStaticClassNames(ReflectiveFixture.class)).contains("PublicNested");
        assertThat(reflectionEngine.getPublicStaticNames(ReflectiveFixture.class))
                .contains("PublicNested", "staticMutableText", "staticGreeting");
    }

    public static class ReflectiveFixture {
        public static String staticMutableText = "static-value";

        public int mutableNumber = 7;

        private final String greetingPrefix;

        public ReflectiveFixture() {
            this("hello");
        }

        public ReflectiveFixture(String greetingPrefix) {
            this.greetingPrefix = greetingPrefix;
        }

        public String greet() {
            return greetingPrefix + " world";
        }

        public String join(String name, Integer count) {
            return greetingPrefix + ":" + name + ":" + count;
        }

        public static String staticGreeting() {
            return staticMutableText;
        }

        public static class PublicNested {
        }
    }
}
