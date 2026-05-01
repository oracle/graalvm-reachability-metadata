/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_py4j.py4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import py4j.reflection.MethodInvoker;
import py4j.reflection.ReflectionEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionEngineTest {
    @Test
    void createsMultidimensionalArraysForNamedTypes() {
        ReflectionEngine engine = new ReflectionEngine();

        Object array = engine.createArray(String.class.getName(), new int[] {2, 3});

        assertThat(array).isInstanceOf(String[][].class);
        String[][] strings = (String[][]) array;
        assertThat(strings.length).isEqualTo(2);
        assertThat(strings[0].length).isEqualTo(3);
    }

    @Test
    void resolvesConstructorsMethodsAndMemberClasses() {
        ReflectionEngine engine = new ReflectionEngine();

        MethodInvoker constructor = engine.getConstructor(
                EngineFixture.class, new Class<?>[] {String.class, Integer.class});
        Object constructed = constructor.invoke(null, new Object[] {"constructed", 7});

        assertThat(constructed).isInstanceOf(EngineFixture.class);
        EngineFixture fixture = (EngineFixture) constructed;
        assertThat(fixture.instanceGreeting("value")).isEqualTo("constructed-value-7");

        Method method = engine.getMethod(EngineFixture.class, "instanceGreeting");
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("instanceGreeting");

        MethodInvoker invoker = engine.getMethod(
                EngineFixture.class, "instanceGreeting", new Class<?>[] {String.class});
        assertThat(engine.invoke(fixture, invoker, new Object[] {"again"})).isEqualTo("constructed-again-7");

        assertThat(engine.getClass(EngineFixture.class, "StaticNested")).isSameAs(EngineFixture.StaticNested.class);
    }

    @Test
    void readsWritesAndListsPublicFields() {
        ReflectionEngine engine = new ReflectionEngine();
        EngineFixture fixture = new EngineFixture("fields", 11);

        Field field = engine.getField(EngineFixture.class, "publicText");

        assertThat(field).isNotNull();
        assertThat(engine.getFieldValue(fixture, field)).isEqualTo("initial");

        engine.setFieldValue(fixture, field, "updated");

        assertThat(fixture.publicText).isEqualTo("updated");
        assertThat(engine.getPublicFieldNames(fixture)).contains("publicText", "number", "STATIC_FIELD");
        assertThat(engine.getPublicStaticFieldNames(EngineFixture.class))
                .contains("STATIC_FIELD", "mutableStaticNumber")
                .doesNotContain("publicText", "number");
    }

    @Test
    void listsPublicMethodAndStaticMemberNames() {
        ReflectionEngine engine = new ReflectionEngine();
        EngineFixture fixture = new EngineFixture("methods", 13);

        assertThat(engine.getPublicMethodNames(fixture)).contains("instanceGreeting", "changeText", "staticGreeting");
        assertThat(engine.getPublicStaticMethodNames(EngineFixture.class))
                .contains("staticGreeting")
                .doesNotContain("instanceGreeting", "changeText");
        assertThat(engine.getPublicStaticClassNames(EngineFixture.class)).contains("StaticNested");
        assertThat(engine.getPublicStaticNames(EngineFixture.class))
                .contains("StaticNested", "STATIC_FIELD", "mutableStaticNumber", "staticGreeting");
    }

    public static class EngineFixture {
        public static final String STATIC_FIELD = "constant";
        public static int mutableStaticNumber = 5;

        public String publicText = "initial";
        public int number;

        private final String prefix;

        public EngineFixture() {
            this("default", 0);
        }

        public EngineFixture(String prefix, int number) {
            this.prefix = prefix;
            this.number = number;
        }

        public String instanceGreeting(String suffix) {
            return prefix + "-" + suffix + "-" + number;
        }

        public void changeText(String value) {
            publicText = value;
        }

        public static String staticGreeting() {
            return "static";
        }

        public static class StaticNested {
            public String nestedName() {
                return "nested";
            }
        }
    }
}
