/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Reflections;

public class ReflectionsTest {
    @Test
    void findsDeclaredMembersAcrossClassHierarchy() throws Exception {
        ChildFixture fixture = new ChildFixture();

        Set<Field> fields = Reflections.getAllDeclaredFields(ChildFixture.class);
        assertThat(fields).extracting(Field::getName).contains("childValue", "parentValue");

        Field field = Reflections.findDeclaredField(ChildFixture.class, "parentValue");
        assertThat(field).isNotNull();
        Reflections.setAccessible(field);
        assertThat(Reflections.getFieldValue(field, fixture, String.class)).isEqualTo("parent");

        assertThat(Reflections.methodExists(ChildFixture.class, "parentGreeting")).isTrue();
        Set<Method> methods = Reflections.getAllDeclaredMethods(ChildFixture.class);
        assertThat(methods).extracting(Method::getName).contains("childGreeting", "parentGreeting");

        Method method = Reflections.findDeclaredMethod(ChildFixture.class, "parentGreeting", String.class);
        assertThat(method).isNotNull();
        assertThat(Reflections.invokeMethod(true, method, String.class, fixture, "Ada")).isEqualTo("parent:Ada");

        Constructor<?> constructor = Reflections.findDeclaredConstructor(ChildFixture.class, String.class);
        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);

        Set<Constructor<?>> constructors = Reflections.getAllDeclaredConstructors(ChildFixture.class);
        assertThat(constructors).extracting(Constructor::getParameterCount).contains(0, 1);
    }

    @Test
    @SuppressWarnings("deprecation")
    void loadsClassesWithSupportedClassLoaderFallbacksAndCreatesInstances() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        String fixtureName = DefaultConstructibleFixture.class.getName();

        try {
            Thread.currentThread().setContextClassLoader(ReflectionsTest.class.getClassLoader());
            assertThat(Reflections.classForName(fixtureName)).isSameAs(DefaultConstructibleFixture.class);

            Thread.currentThread().setContextClassLoader(null);
            assertThat(Reflections.classForName(String.class.getName())).isSameAs(String.class);

            Thread.currentThread().setContextClassLoader(new RejectingClassLoader(originalClassLoader, fixtureName));
            assertThat(Reflections.classForName(fixtureName, ReflectionsTest.class.getClassLoader()))
                    .isSameAs(DefaultConstructibleFixture.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        DefaultConstructibleFixture fixture = Reflections.newInstance(DefaultConstructibleFixture.class);
        assertThat(fixture.value()).isEqualTo("created");
    }

    @Test
    void identifiesFinalMethodsAndResolvesRawListItemTypes() throws Exception {
        Object methodOrType = Reflections.getNonPrivateFinalMethodOrType(FinalMethodFixture.class);
        assertThat(methodOrType).isInstanceOf(Method.class);
        assertThat(((Method) methodOrType).getName()).isEqualTo("finalMethod");

        RawListFixture fixture = new RawListFixture();
        Field rawItems = Reflections.findDeclaredField(RawListFixture.class, "rawItems");
        assertThat(rawItems).isNotNull();
        Reflections.setAccessible(rawItems);
        assertThat(Reflections.resolveListType(rawItems, fixture)).isEqualTo(String.class);
    }

    public static class DefaultConstructibleFixture {
        public DefaultConstructibleFixture() {
        }

        String value() {
            return "created";
        }
    }

    private static class ParentFixture {
        private String parentValue = "parent";

        String parentGreeting(String name) {
            return "parent:" + name;
        }
    }

    private static class ChildFixture extends ParentFixture {
        private int childValue = 42;

        ChildFixture() {
        }

        ChildFixture(String parentValue) {
            super.parentValue = parentValue;
        }

        String childGreeting() {
            return "child";
        }
    }

    private static class FinalMethodFixture {
        final String finalMethod() {
            return "final";
        }
    }

    private static class RawListFixture {
        @SuppressWarnings("rawtypes")
        private List rawItems = Collections.singletonList("item");
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedName;

        RejectingClassLoader(ClassLoader parent, String rejectedName) {
            super(parent);
            this.rejectedName = rejectedName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
