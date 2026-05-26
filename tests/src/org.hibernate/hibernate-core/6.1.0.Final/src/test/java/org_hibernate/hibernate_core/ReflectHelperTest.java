/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.type.Type;
import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.Course;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectHelperTest {

    @Test
    @SuppressWarnings("deprecation")
    public void classForNameUsesContextClassLoaderAndFallbacks() throws Exception {
        Class<?> loadedByContextClassLoader = withContextClassLoader(
                ReflectHelperTest.class.getClassLoader(),
                () -> ReflectHelper.classForName("java.lang.String"));
        assertThat(loadedByContextClassLoader).isSameAs(String.class);

        Class<?> loadedByDeprecatedFallback = withContextClassLoader(
                null,
                () -> ReflectHelper.classForName("java.lang.String"));
        assertThat(loadedByDeprecatedFallback).isSameAs(String.class);

        Class<?> loadedByCallerFallback = withContextClassLoader(
                null,
                () -> ReflectHelper.classForName("java.lang.String", ReflectHelperTest.class));
        assertThat(loadedByCallerFallback).isSameAs(String.class);
    }

    @Test
    public void constructorAndMethodHelpersResolveAccessibleMembers() {
        Type[] noTypes = new Type[0];
        Constructor<?> publicConstructor = ReflectHelper.getConstructor(Course.class, noTypes);
        assertThat(publicConstructor).isNotNull();
        assertThat(publicConstructor.getParameterCount()).isZero();

        Constructor<Course> declaredConstructor = ReflectHelper.getConstructor(Course.class);
        assertThat(declaredConstructor).isNotNull();
        assertThat(declaredConstructor.getParameterCount()).isZero();

        Method getter = ReflectHelper.getMethod(Course.class, "getId");
        assertThat(getter).isNotNull();
        assertThat(getter.getReturnType()).isSameAs(Long.class);
    }

    @Test
    public void findGetterMethodChecksGetAndIsVariants() {
        Method getOnlyGetter = ReflectHelper.findGetterMethod(GetOnlyBean.class, "name");
        assertThat(getOnlyGetter.getName()).isEqualTo("getName");

        Method isOnlyGetter = ReflectHelper.findGetterMethod(IsOnlyBean.class, "enabled");
        assertThat(isOnlyGetter.getName()).isEqualTo("isEnabled");
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            return supplier.get();
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static class GetOnlyBean {
        public String getName() {
            return "hibernate";
        }
    }

    public static class IsOnlyBean {
        public boolean isEnabled() {
            return true;
        }
    }

}
