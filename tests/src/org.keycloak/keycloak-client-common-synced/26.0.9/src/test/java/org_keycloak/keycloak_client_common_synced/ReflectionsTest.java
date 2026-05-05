/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionsTest {
    @Test
    void findsDeclaredMembersAcrossClassHierarchy() {
        Set<Field> fields = Reflections.getAllDeclaredFields(ReflectionTarget.class);
        Set<Method> methods = Reflections.getAllDeclaredMethods(ReflectionTarget.class);
        Set<Constructor<?>> constructors = Reflections.getAllDeclaredConstructors(ReflectionTarget.class);

        Field inheritedField = Reflections.findDeclaredField(ReflectionTarget.class, "inheritedValue");
        Field ownField = Reflections.findDeclaredField(ReflectionTarget.class, "ownValue");
        Method inheritedMethod = Reflections.findDeclaredMethod(
                ReflectionTarget.class, "inheritedGreeting", String.class);
        Method ownMethod = Reflections.findDeclaredMethod(ReflectionTarget.class, "ownGreeting", String.class);
        Constructor<?> constructor = Reflections.findDeclaredConstructor(ReflectionTarget.class);

        assertThat(fields).extracting(Field::getName).contains("inheritedValue", "ownValue");
        assertThat(methods).extracting(Method::getName).contains("inheritedGreeting", "ownGreeting");
        assertThat(constructors).isNotEmpty();
        assertThat(inheritedField).isNotNull();
        assertThat(ownField).isNotNull();
        assertThat(inheritedMethod).isNotNull();
        assertThat(ownMethod).isNotNull();
        assertThat(constructor).isNotNull();
        assertThat(Reflections.methodExists(ReflectionTarget.class, "inheritedGreeting")).isTrue();
        assertThat(Reflections.methodExists(ReflectionTarget.class, "missingGreeting")).isFalse();
    }

    @Test
    void invokesMethodsAndReadsFields() {
        ReflectionTarget target = new ReflectionTarget();
        Method ownGreeting = Reflections.findDeclaredMethod(ReflectionTarget.class, "ownGreeting", String.class);
        Field ownValue = Reflections.findDeclaredField(ReflectionTarget.class, "ownValue");

        String greeting = Reflections.invokeMethod(ownGreeting, String.class, target, "Ada");
        Integer value = Reflections.getFieldValue(ownValue, target, Integer.class);

        assertThat(greeting).isEqualTo("own:Ada");
        assertThat(value).isEqualTo(42);
    }

    @Test
    void resolvesRawListItemTypeFromFieldValue() throws IllegalAccessException {
        ReflectionTarget target = new ReflectionTarget();
        Field rawItems = Reflections.findDeclaredField(ReflectionTarget.class, "rawItems");

        Class<?> itemType = Reflections.resolveListType(rawItems, target);

        assertThat(itemType).isEqualTo(String.class);
    }

    @Test
    void detectsNonPrivateFinalMethod() {
        Object methodOrType = Reflections.getNonPrivateFinalMethodOrType(ReflectionTarget.class);

        assertThat(methodOrType).isInstanceOf(Method.class);
        assertThat(((Method) methodOrType).getName()).isEqualTo("finalGreeting");
    }

    @Test
    void loadsClassesWithContextClassLoaderDefaultClassForNameAndAdditionalLoader() throws ClassNotFoundException {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ReflectionsTest.class.getClassLoader());
            Class<?> viaContextLoader = Reflections.classForName(ReflectionTarget.class.getName());
            assertThat(viaContextLoader).isSameAs(ReflectionTarget.class);

            Thread.currentThread().setContextClassLoader(null);
            Class<?> viaDefaultClassForName = Reflections.classForName(ReflectionTarget.class.getName());
            assertThat(viaDefaultClassForName).isSameAs(ReflectionTarget.class);

            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());
            Class<?> viaAdditionalLoader = Reflections.classForName(
                    ReflectionTarget.class.getName(), ReflectionsTest.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(ReflectionsTest.class.getClassLoader());
            assertThat(viaAdditionalLoader).isSameAs(ReflectionTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void createsNewInstanceByClassName() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        ReflectionTarget target = Reflections.newInstance(ReflectionTarget.class);

        assertThat(target.ownGreeting("Grace")).isEqualTo("own:Grace");
    }

    public static class ReflectionParent {
        public String inheritedValue = "parent";

        public String inheritedGreeting(String name) {
            return "inherited:" + name;
        }
    }

    public static class ReflectionTarget extends ReflectionParent {
        public Integer ownValue = 42;
        @SuppressWarnings("rawtypes")
        public List rawItems = List.of("first");

        public ReflectionTarget() {
        }

        public String ownGreeting(String name) {
            return "own:" + name;
        }

        public final String finalGreeting() {
            return "final";
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
