/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.reflections.Reflections;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionsTest {
    @Test
    void discoversMembersAcrossClassHierarchy() {
        Set<Field> fields = Reflections.getAllDeclaredFields(SampleBean.class);
        assertThat(fields)
                .extracting(Field::getName)
                .contains("secret", "inheritedField", "rawItems");

        Field secret = Reflections.findDeclaredField(SampleBean.class, "secret");
        assertThat(secret).isNotNull();
        assertThat(secret.getDeclaringClass()).isEqualTo(SampleBean.class);

        Field inherited = Reflections.findDeclaredField(SampleBean.class, "inheritedField");
        assertThat(inherited).isNotNull();
        assertThat(inherited.getDeclaringClass()).isEqualTo(ParentSample.class);

        Set<Method> methods = Reflections.getAllDeclaredMethods(SampleBean.class);
        assertThat(methods)
                .extracting(Method::getName)
                .contains("join", "inheritedMethod");

        assertThat(Reflections.methodExists(SampleBean.class, "inheritedMethod"))
                .isTrue();
        assertThat(Reflections.findDeclaredMethod(SampleBean.class, "join", String.class, int.class))
                .isNotNull();

        Set<Constructor<?>> constructors = Reflections.getAllDeclaredConstructors(SampleBean.class);
        assertThat(constructors)
                .anyMatch(constructor -> constructor.getParameterCount() == 0)
                .anyMatch(constructor -> Arrays.equals(constructor.getParameterTypes(), new Class<?>[] {String.class}));
        assertThat(Reflections.findDeclaredConstructor(SampleBean.class, String.class))
                .isNotNull();
    }

    @Test
    void invokesMethodsAndReadsFields() {
        SampleBean sample = new SampleBean();

        Field secret = Reflections.findDeclaredField(SampleBean.class, "secret");
        Reflections.setAccessible(secret);
        assertThat(Reflections.getFieldValue(secret, sample, String.class))
                .isEqualTo("initial");

        Method join = Reflections.findDeclaredMethod(SampleBean.class, "join", String.class, int.class);
        assertThat(Reflections.invokeMethod(true, join, String.class, sample, "value", 7))
                .isEqualTo("value:7:initial");

        Object finalMember = Reflections.getNonPrivateFinalMethodOrType(SampleBean.class);
        assertThat(finalMember)
                .isInstanceOf(Method.class);
        assertThat(((Method) finalMember).getName())
                .isEqualTo("finalMethod");
    }

    @Test
    void loadsClassesWithContextFallbackAndDefaultClassLoaders() throws Exception {
        assertThat(Reflections.classForName(SampleBean.class.getName()))
                .isEqualTo(SampleBean.class);

        Thread thread = Thread.currentThread();
        ClassLoader originalContextLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertThat(Reflections.classForName(SampleBean.class.getName()))
                    .isEqualTo(SampleBean.class);
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }

        ClassLoader sampleLoader = SampleBean.class.getClassLoader();
        ClassLoader rejectingContextLoader = new RejectingClassLoader(sampleLoader, SampleBean.class.getName());
        try {
            thread.setContextClassLoader(rejectingContextLoader);
            assertThat(Reflections.classForName(SampleBean.class.getName(), sampleLoader))
                    .isEqualTo(SampleBean.class);
        } finally {
            thread.setContextClassLoader(originalContextLoader);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void createsInstancesAndResolvesRawListItemTypes() throws Exception {
        SampleBean sample = Reflections.newInstance(SampleBean.class);
        assertThat(sample).isInstanceOf(SampleBean.class);

        Field rawItems = Reflections.findDeclaredField(SampleBean.class, "rawItems");
        Reflections.setAccessible(rawItems);
        assertThat(Reflections.resolveListType(rawItems, sample))
                .isEqualTo(String.class);

        Field typedItems = Reflections.findDeclaredField(SampleBean.class, "typedItems");
        Reflections.setAccessible(typedItems);
        assertThat(Reflections.resolveListType(typedItems, sample))
                .isEqualTo(Integer.class);
    }

    public static class ParentSample {
        private String inheritedField = "from-parent";

        public ParentSample() {
        }

        protected String inheritedMethod() {
            return inheritedField;
        }
    }

    public static class SampleBean extends ParentSample {
        private String secret = "initial";

        @SuppressWarnings("rawtypes")
        private List rawItems = Arrays.asList("raw-value");

        private List<Integer> typedItems = Arrays.asList(1, 2, 3);

        public SampleBean() {
        }

        private SampleBean(String secret) {
            this.secret = secret;
        }

        private String join(String prefix, int value) {
            return prefix + ":" + value + ":" + secret;
        }

        public final String finalMethod() {
            return "final";
        }
    }

    private static class RejectingClassLoader extends ClassLoader {
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
