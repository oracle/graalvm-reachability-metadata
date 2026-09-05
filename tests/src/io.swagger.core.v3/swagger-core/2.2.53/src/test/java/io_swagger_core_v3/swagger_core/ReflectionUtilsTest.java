/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_core;

import io.swagger.v3.core.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    private static final String CONTEXT_ONLY_CLASS_NAME =
            "io_swagger_core_v3.swagger_core.context.ContextOnlyFixture";

    @Test
    void loadsExistingClassByName() throws ClassNotFoundException {
        Class<?> loadedClass = ReflectionUtils.loadClassByName(String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    void fallsBackToThreadContextClassLoader() throws ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ContextOnlyClassLoader contextClassLoader = new ContextOnlyClassLoader(originalClassLoader);
        thread.setContextClassLoader(contextClassLoader);

        try {
            Class<?> loadedClass = ReflectionUtils.loadClassByName(CONTEXT_ONLY_CLASS_NAME);

            assertThat(loadedClass).isEqualTo(ContextOnlyFixture.class);
            assertThat(contextClassLoader.requestedClassName).isEqualTo(CONTEXT_ONLY_CLASS_NAME);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void returnsSortedDeclaredFieldsWithoutHiddenSuperclassFields() {
        List<Field> fields = ReflectionUtils.getDeclaredFields(ChildFields.class);

        assertThat(fields).hasSize(3);
        assertThat(fields.get(0).getName()).isEqualTo("childOnly");
        assertThat(fields.get(1).getName()).isEqualTo("shared");
        assertThat(fields.get(1).getDeclaringClass()).isEqualTo(ChildFields.class);
        assertThat(fields.get(2).getName()).isEqualTo("superOnly");
    }

    @Test
    void findsAndReadsPublicField() {
        PublicFieldFixture fixture = new PublicFieldFixture("field-value");

        Field field = ReflectionUtils.findField("value", PublicFieldFixture.class);
        Optional<Object> value = ReflectionUtils.safeGet(field, fixture);

        assertThat(field).isNotNull();
        assertThat(value).contains("field-value");
    }

    @Test
    void findsAnnotatedDeclaredMethod() {
        List<Method> methods = ReflectionUtils.getAnnotatedMethods(AnnotatedMethods.class, Marker.class);

        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getName()).isEqualTo("marked");
    }

    @Test
    void detectsAndFindsOverriddenMethod() throws NoSuchMethodException {
        Method childMethod = OverridingChild.class.getMethod("format", String.class);

        assertThat(ReflectionUtils.hasOverriddenMethods(childMethod, OverridingChild.class)).isTrue();
        assertThat(ReflectionUtils.isOverriddenMethod(childMethod, OverridingChild.class)).isTrue();

        Method parentMethod = ReflectionUtils.findMethod(childMethod, OverridingParent.class);
        assertThat(parentMethod).isNotNull();
        assertThat(parentMethod.getDeclaringClass()).isEqualTo(OverridingParent.class);
    }

    @Test
    void invokesPublicMethodSafely() throws NoSuchMethodException {
        InvocationFixture fixture = new InvocationFixture("prefix");
        Method method = InvocationFixture.class.getMethod("join", String.class);

        Optional<Object> value = ReflectionUtils.safeInvoke(method, fixture, "value");

        assertThat(value).contains("prefix-value");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
    }

    public static class ParentFields {
        public String shared;
        public String superOnly;
    }

    public static class ContextOnlyFixture {
    }

    private static final class ContextOnlyClassLoader extends ClassLoader {
        private String requestedClassName;

        private ContextOnlyClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_CLASS_NAME.equals(name)) {
                requestedClassName = name;
                return ContextOnlyFixture.class;
            }
            return super.loadClass(name);
        }
    }

    public static class ChildFields extends ParentFields {
        public String childOnly;
        public String shared;
    }

    public static class PublicFieldFixture {
        public String value;

        PublicFieldFixture(String value) {
            this.value = value;
        }
    }

    public static class AnnotatedMethods {
        @Marker
        public void marked() {
        }

        public void unmarked() {
        }
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

    public static class InvocationFixture {
        private final String prefix;

        InvocationFixture(String prefix) {
            this.prefix = prefix;
        }

        public String join(String value) {
            return prefix + "-" + value;
        }
    }
}
