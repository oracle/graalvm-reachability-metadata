/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @Test
    void visitsDeclaredNestedClasses() {
        Set<String> simpleNames = new HashSet<>();

        ReflectionHelper.doWithClasses(ClassesFixture.class, clazz -> simpleNames.add(clazz.getSimpleName()));

        assertThat(simpleNames).contains("FirstNested", "SecondNested");
    }

    @Test
    void visitsDeclaredAndInheritedFields() {
        Set<String> fieldNames = new HashSet<>();

        ReflectionHelper.doWithFields(FieldAccessFixture.class, field -> fieldNames.add(field.getName()));

        assertThat(fieldNames).contains("text", "enabled", "parentValue");
    }

    @Test
    void visitsDeclaredAndInheritedMethods() {
        Set<String> methodNames = new HashSet<>();

        ReflectionHelper.doWithMethods(MethodFixture.class, method -> methodNames.add(method.getName()));

        assertThat(methodNames).contains("childMethod", "parentMethod");
    }

    @Test
    void findsDeclaredMethodOnClass() {
        Method method = ReflectionHelper.findMethod(MethodFixture.class, "childMethod", String.class);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("childMethod");
    }

    @Test
    void findsPublicMethodOnInterface() {
        Method method = ReflectionHelper.findMethod(ChildContract.class, "parentContractMethod");

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("parentContractMethod");
    }

    @Test
    void setsAndGetsPrimitiveAndReferenceFields() {
        FieldAccessFixture fixture = new FieldAccessFixture();
        Map<String, Field> fields = fieldsByName(FieldAccessFixture.class);

        ReflectionHelper.setField(fields.get("enabled"), fixture, "true");
        ReflectionHelper.setField(fields.get("byteValue"), fixture, "7");
        ReflectionHelper.setField(fields.get("intValue"), fixture, "11");
        ReflectionHelper.setField(fields.get("longValue"), fixture, "13");
        ReflectionHelper.setField(fields.get("floatValue"), fixture, "1.5");
        ReflectionHelper.setField(fields.get("doubleValue"), fixture, "2.5");
        ReflectionHelper.setField(fields.get("text"), fixture, "camel");

        assertThat(ReflectionHelper.getField(fields.get("enabled"), fixture)).isEqualTo(true);
        assertThat(ReflectionHelper.getField(fields.get("byteValue"), fixture)).isEqualTo((byte) 7);
        assertThat(ReflectionHelper.getField(fields.get("intValue"), fixture)).isEqualTo(11);
        assertThat(ReflectionHelper.getField(fields.get("longValue"), fixture)).isEqualTo(13L);
        assertThat(ReflectionHelper.getField(fields.get("floatValue"), fixture)).isEqualTo(1.5F);
        assertThat(ReflectionHelper.getField(fields.get("doubleValue"), fixture)).isEqualTo(2.5D);
        assertThat(ReflectionHelper.getField(fields.get("text"), fixture)).isEqualTo("camel");
    }

    private static Map<String, Field> fieldsByName(Class<?> clazz) {
        Map<String, Field> fields = new HashMap<>();

        ReflectionHelper.doWithFields(clazz, field -> fields.put(field.getName(), field));

        return fields;
    }

    private static final class ClassesFixture {
        private static final class FirstNested {
        }

        private static final class SecondNested {
        }
    }

    private static class ParentFieldAccessFixture {
        private int parentValue;
    }

    private static final class FieldAccessFixture extends ParentFieldAccessFixture {
        private boolean enabled;
        private byte byteValue;
        private int intValue;
        private long longValue;
        private float floatValue;
        private double doubleValue;
        private String text;
    }

    private static class ParentMethodFixture {
        void parentMethod() {
        }
    }

    private static final class MethodFixture extends ParentMethodFixture {
        void childMethod(String value) {
        }
    }

    private interface ParentContract {
        void parentContractMethod();
    }

    private interface ChildContract extends ParentContract {
        void childContractMethod();
    }
}
