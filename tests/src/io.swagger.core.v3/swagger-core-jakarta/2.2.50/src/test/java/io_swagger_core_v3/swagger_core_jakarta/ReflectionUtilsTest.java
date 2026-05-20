/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core_jakarta;

import io.swagger.v3.core.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReflectionUtilsTest {
    @Test
    void loadsExistingClassByName() throws ClassNotFoundException {
        Class<?> loadedClass = ReflectionUtils.loadClassByName(String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    void delegatesToContextClassLoaderWhenClassForNameCannotResolveName() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ReflectionUtilsTest.class.getClassLoader());

            assertThatThrownBy(() -> ReflectionUtils.loadClassByName("example.missing.ReflectionUtilsFixture"))
                    .isInstanceOf(ClassNotFoundException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void returnsSortedDeclaredFieldsFromClassHierarchyWithoutHiddenSuperclassFields() {
        List<Field> fields = ReflectionUtils.getDeclaredFields(ChildFields.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactly("childOnly", "shared", "superOnly");
        assertThat(fields.get(1).getDeclaringClass()).isEqualTo(ChildFields.class);
    }

    @Test
    void detectsOverrideAndFindsOverriddenMethod() throws NoSuchMethodException {
        Method childMethod = OverridingChild.class.getMethod("format", String.class);

        assertThat(ReflectionUtils.hasOverriddenMethods(childMethod, OverridingChild.class)).isTrue();
        assertThat(ReflectionUtils.isOverriddenMethod(childMethod, OverridingChild.class)).isTrue();

        Method overriddenMethod = ReflectionUtils.findMethod(childMethod, OverridingParent.class);
        assertThat(overriddenMethod).isNotNull();
        assertThat(overriddenMethod.getDeclaringClass()).isEqualTo(OverridingParent.class);
        assertThat(overriddenMethod.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void invokesPublicMethodSafely() throws NoSuchMethodException {
        SafeInvokeFixture fixture = new SafeInvokeFixture("prefix");
        Method method = SafeInvokeFixture.class.getMethod("join", String.class);

        Optional<Object> result = ReflectionUtils.safeInvoke(method, fixture, "value");

        assertThat(result).contains("prefix-value");
    }

    public static class ParentFields {
        public String shared;
        public String superOnly;
    }

    public static class ChildFields extends ParentFields {
        public String childOnly;
        public String shared;
    }

    public static class OverridingParent {
        public String format(String value) {
            return value;
        }
    }

    public static class OverridingChild extends OverridingParent {
        @Override
        public String format(String value) {
            return "child-" + value;
        }

        public CharSequence format(CharSequence value) {
            return "overload-" + value;
        }
    }

    public static class SafeInvokeFixture {
        private final String prefix;

        public SafeInvokeFixture(String prefix) {
            this.prefix = prefix;
        }

        public String join(String value) {
            return prefix + "-" + value;
        }
    }
}
