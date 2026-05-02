/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {

    @Test
    void createsInstanceFromMatchingDeclaredConstructor() {
        InstantiatedSubject subject = ReflectionUtils.newInstance(InstantiatedSubject.class, "created");

        assertThat(subject.getValue()).isEqualTo("created");
    }

    @Test
    void findsDeclaredConstructorsMatchingPredicate() {
        List<Constructor<?>> constructors = ReflectionUtils.findConstructors(ConstructorSubject.class,
                constructor -> constructor.getParameterCount() == 1);

        assertThat(constructors).hasSize(1);
        assertThat(constructors.get(0).getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void readsPrivateFieldValueByName() throws Exception {
        FieldSubject subject = new FieldSubject("field-value");

        Object value = ReflectionUtils.tryToReadFieldValue(FieldSubject.class, "value", subject).get();

        assertThat(value).isEqualTo("field-value");
    }

    @SuppressWarnings("deprecation")
    @Test
    void resolvesOutermostInstanceFromInnerClass() {
        OuterSubject outer = new OuterSubject("outer-value");
        OuterSubject.InnerSubject inner = outer.new InnerSubject();

        Optional<Object> outermostInstance = ReflectionUtils.getOutermostInstance(inner, OuterSubject.class);

        assertThat(outermostInstance).hasValueSatisfying(value -> assertThat(value).isSameAs(outer));
    }

    @Test
    void findsFieldsDeclaredByImplementedInterfaces() {
        List<Field> fields = ReflectionUtils.findFields(InterfaceFieldSubject.class,
                field -> field.getName().equals("INTERFACE_FIELD"), HierarchyTraversalMode.TOP_DOWN);

        assertThat(fields).extracting(Field::getName).containsExactly("INTERFACE_FIELD");
    }

    @Test
    void getsPublicMethodByNameAndParameterTypes() throws Exception {
        Method method = ReflectionUtils.tryToGetMethod(MethodSubject.class, "echo", String.class).get();

        assertThat(method.getName()).isEqualTo("echo");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void findsMethodsDeclaredByInterfaces() {
        Optional<Method> method = ReflectionUtils.findMethod(InterfaceMethodSubject.class, "interfaceMethod");

        assertThat(method).hasValueSatisfying(value -> assertThat(value.getName()).isEqualTo("interfaceMethod"));
    }

    @Test
    void loadsObjectArrayTypesBySourceName() throws Exception {
        Class<?> arrayType = ReflectionUtils.tryToLoadClass("java.util.UUID[][]").get();

        assertThat(arrayType).isEqualTo(UUID[][].class);
    }

    public static class InstantiatedSubject {

        private final String value;

        public InstantiatedSubject(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    public static class ConstructorSubject {

        public ConstructorSubject() {
        }

        public ConstructorSubject(String value) {
        }
    }

    public static class FieldSubject {

        private final String value;

        FieldSubject(String value) {
            this.value = value;
        }
    }

    public static class OuterSubject {

        private final String value;

        OuterSubject(String value) {
            this.value = value;
        }

        public class InnerSubject {

            String getOuterValue() {
                return value;
            }
        }
    }

    public interface InterfaceFields {

        String INTERFACE_FIELD = "interface-field";
    }

    public static class InterfaceFieldSubject implements InterfaceFields {
    }

    public static class MethodSubject {

        public String echo(String value) {
            return value;
        }
    }

    public interface InterfaceMethodSubject {

        void interfaceMethod();
    }
}
