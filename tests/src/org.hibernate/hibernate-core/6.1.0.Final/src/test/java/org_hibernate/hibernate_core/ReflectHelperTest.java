/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.MappingException;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.type.Type;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectHelperTest {
    private static final String HIBERNATE_EXCEPTION_CLASS = "org.hibernate.MappingException";
    private static final String REFLECT_HELPER_CLASS = "org.hibernate.internal.util.ReflectHelper";

    @Test
    public void classForNameUsesContextClassLoader() throws Exception {
        Class<?> loadedClass = withContextClassLoader(
                ReflectHelperTest.class.getClassLoader(),
                () -> ReflectHelper.classForName(HIBERNATE_EXCEPTION_CLASS, ReflectHelperTest.class)
        );

        assertThat(loadedClass).isEqualTo(MappingException.class);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void classForNameFallsBackWhenContextClassLoaderIsUnavailable() throws Exception {
        Class<?> callerLoadedClass = withContextClassLoader(
                null,
                () -> ReflectHelper.classForName(REFLECT_HELPER_CLASS, ReflectHelperTest.class)
        );
        Class<?> defaultLoadedClass = withContextClassLoader(
                null,
                () -> ReflectHelper.classForName(HIBERNATE_EXCEPTION_CLASS)
        );

        assertThat(callerLoadedClass).isEqualTo(ReflectHelper.class);
        assertThat(defaultLoadedClass).isEqualTo(MappingException.class);
    }

    @Test
    public void getConstructorMatchesPublicConstructorByMappedTypeArity() {
        Constructor<?> constructor = ReflectHelper.getConstructor(
                PublicConstructorBean.class,
                new Type[]{null}
        );

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterCount()).isEqualTo(1);
    }

    @Test
    public void getConstructorFindsDeclaredConstructor() {
        Constructor<DeclaredConstructorBean> constructor = ReflectHelper.getConstructor(
                DeclaredConstructorBean.class,
                String.class
        );

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterCount()).isEqualTo(1);
    }

    @Test
    public void getMethodFindsPublicMethod() {
        Method method = ReflectHelper.getMethod(GetterBean.class, "getStatus");

        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    public void findGetterMethodFindsGetVariantAndChecksForIsVariant() {
        Method method = ReflectHelper.findGetterMethod(GetterBean.class, "status");

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("getStatus");
    }

    @Test
    public void findGetterMethodFindsIsVariantAndChecksForGetVariant() {
        Method method = ReflectHelper.findGetterMethod(GetterBean.class, "active");

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("isActive");
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ClassLoadingAction<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.run();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private interface ClassLoadingAction<T> {
        T run() throws Exception;
    }

    public static final class PublicConstructorBean {
        private final String name;

        public PublicConstructorBean(String name) {
            this.name = name;
        }
    }

    public static final class DeclaredConstructorBean {
        private final String name;

        private DeclaredConstructorBean(String name) {
            this.name = name;
        }
    }

    public static final class GetterBean {
        public String getStatus() {
            return "ready";
        }

        public boolean isActive() {
            return true;
        }
    }
}
