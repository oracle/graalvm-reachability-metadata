/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import static org.assertj.core.api.Assertions.assertThat;

import com.atomikos.util.ClassLoadingHelper;
import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;

public class ClassLoadingHelperTest {
    @Test
    void loadsClassWithContextClassLoaderAndClassForNameFallback() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> loadedWithContextClassLoader = ClassLoadingHelper.loadClass(PublicBean.class.getName());
            assertThat(loadedWithContextClassLoader).isSameAs(PublicBean.class);

            Thread.currentThread().setContextClassLoader(new RejectingClassLoader(originalContextClassLoader));
            Class<?> loadedWithClassForNameFallback = ClassLoadingHelper.loadClass(PublicBean.class.getName());
            assertThat(loadedWithClassForNameFallback).isSameAs(PublicBean.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void createsNewInstanceByClassName() {
        Object instance = ClassLoadingHelper.newInstance(PublicBean.class.getName());

        assertThat(instance).isInstanceOf(PublicBean.class);
        assertThat(((PublicBean) instance).message()).isEqualTo("created");
    }

    @Test
    void createsProxyAndReusesLastGoodClassLoader() {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if ("greet".equals(method.getName())) {
                return "Hello " + arguments[0];
            }
            throw new UnsupportedOperationException(method.getName());
        };

        GreetingService firstProxy = ClassLoadingHelper.newProxyInstance(
                classLoadersToTry(), GreetingService.class, new Class<?>[] {GreetingService.class}, handler);
        GreetingService secondProxy = ClassLoadingHelper.newProxyInstance(
                classLoadersToTry(), GreetingService.class, new Class<?>[] {GreetingService.class}, handler);

        assertThat(firstProxy.greet("Atomikos")).isEqualTo("Hello Atomikos");
        assertThat(secondProxy.greet("GraalVM")).isEqualTo("Hello GraalVM");
    }

    @Test
    void loadsPackageRelativeAndAbsoluteResourcesFromClasspath() {
        URL packageResource = ClassLoadingHelper.loadResourceFromClasspath(
                ClassLoadingHelperTest.class, "classloading-helper-package-resource.txt");
        URL absoluteResource = ClassLoadingHelper.loadResourceFromClasspath(
                ClassLoadingHelperTest.class, "classloading-helper-absolute-resource.txt");

        assertThat(packageResource).isNotNull();
        assertThat(absoluteResource).isNotNull();
    }

    private static Deque<ClassLoader> classLoadersToTry() {
        Deque<ClassLoader> classLoaders = new ArrayDeque<>();
        classLoaders.add(ClassLoadingHelperTest.class.getClassLoader());
        return classLoaders;
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class PublicBean {
        public String message() {
            return "created";
        }
    }

    private static class RejectingClassLoader extends ClassLoader {
        RejectingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
