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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    @Test
    void createsInstancesAndExposesClassMembers() {
        Constructed value = ClassUtil.createInstance(Constructed.class, false);
        Constructor<Constructed> constructor = ClassUtil.findConstructor(Constructed.class, false);
        ClassUtil.Ctor[] constructors = ClassUtil.getConstructors(Constructed.class);
        Field[] fields = ClassUtil.getDeclaredFields(Constructed.class);
        Method[] declaredMethods = ClassUtil.getDeclaredMethods(Constructed.class);
        Method[] classMethods = ClassUtil.getClassMethods(Constructed.class);

        assertThat(value.name()).isEqualTo("created");
        assertThat(constructor).isNotNull();
        assertThat(constructors).isNotEmpty();
        assertThat(fields).extracting(Field::getName).contains("name");
        assertThat(declaredMethods).extracting(Method::getName).contains("name");
        assertThat(classMethods).extracting(Method::getName).contains("name", "toString");
    }

    @Test
    void findsAnnotatedEnumConstant() {
        Enum<?> result = ClassUtil.findFirstAnnotatedEnumValue(
                annotatedEnumClass(), Preferred.class);

        assertThat(result).isEqualTo(Choice.SECOND);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<Enum<?>> annotatedEnumClass() {
        return (Class) Choice.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Preferred {
    }

    public enum Choice {
        FIRST,
        @Preferred
        SECOND
    }

    public static class Constructed {
        private final String name;

        public Constructed() {
            name = "created";
        }

        public String name() {
            return name;
        }
    }
}
