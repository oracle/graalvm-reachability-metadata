/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import com.atomikos.util.ClassLoadingHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingHelperTest {

    private static final String PACKAGE_RESOURCE = "classloadinghelper-package-resource.txt";
    private static final String ROOT_RESOURCE = "classloadinghelper-root-resource.txt";

    @Test
    void loadClassUsesTheContextClassLoaderWhenItCanResolveTheType() throws ClassNotFoundException {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(originalContextClassLoader);

        Thread.currentThread().setContextClassLoader(trackingClassLoader);
        try {
            Class<?> loadedClass = ClassLoadingHelper.loadClass(LoadableFixture.class.getName());

            assertThat(loadedClass).isEqualTo(LoadableFixture.class);
            assertThat(trackingClassLoader.requestedClassNames()).contains(LoadableFixture.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadClassFallsBackToClassForNameWhenTheContextClassLoaderCannotResolveTheType() throws ClassNotFoundException {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
            originalContextClassLoader,
            LoadableFixture.class.getName()
        );

        Thread.currentThread().setContextClassLoader(rejectingClassLoader);
        try {
            Class<?> loadedClass = ClassLoadingHelper.loadClass(LoadableFixture.class.getName());

            assertThat(loadedClass).isEqualTo(LoadableFixture.class);
            assertThat(rejectingClassLoader.rejectedClassNames()).containsExactly(LoadableFixture.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void newInstanceCreatesAnInstanceFromTheProvidedClassName() {
        Object instance = ClassLoadingHelper.newInstance(NoArgConstructible.class.getName());

        assertThat(instance).isInstanceOf(NoArgConstructible.class);
    }

    @Test
    void newProxyInstanceCreatesWorkingProxiesAndReusesTheLastSuccessfulClassLoader() {
        Deque<ClassLoader> firstClassLoadersToTry = new ArrayDeque<>();
        firstClassLoadersToTry.add(ProxyContract.class.getClassLoader());

        ProxyContract firstProxy = ClassLoadingHelper.newProxyInstance(
            firstClassLoadersToTry,
            ProxyContract.class,
            new Class<?>[] {ProxyContract.class, SecondaryContract.class},
            new GreetingInvocationHandler("hello ")
        );

        assertThat(firstProxy.greet("team")).isEqualTo("hello team");
        assertThat(firstProxy).isInstanceOf(SecondaryContract.class);

        Deque<ClassLoader> secondClassLoadersToTry = new ArrayDeque<>();
        secondClassLoadersToTry.add(ProxyContract.class.getClassLoader());

        ProxyContract secondProxy = ClassLoadingHelper.newProxyInstance(
            secondClassLoadersToTry,
            ProxyContract.class,
            new Class<?>[] {ProxyContract.class, SecondaryContract.class},
            new GreetingInvocationHandler("hi ")
        );

        assertThat(secondProxy.greet("team")).isEqualTo("hi team");
        assertThat(secondProxy).isInstanceOf(SecondaryContract.class);
    }

    @Test
    void loadResourceFromClasspathFindsResourcesRelativeToTheReferenceClassPackage() {
        URL resource = ClassLoadingHelper.loadResourceFromClasspath(ClassLoadingHelperTest.class, PACKAGE_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains(PACKAGE_RESOURCE);
    }

    @Test
    void loadResourceFromClasspathFallsBackToTheClasspathRoot() {
        URL resource = ClassLoadingHelper.loadResourceFromClasspath(ClassLoadingHelperTest.class, ROOT_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains(ROOT_RESOURCE);
    }

    public interface ProxyContract {

        String greet(String name);
    }

    public interface SecondaryContract {
    }

    public static final class LoadableFixture {
    }

    public static final class NoArgConstructible {

        public NoArgConstructible() {
        }
    }

    private static final class GreetingInvocationHandler implements InvocationHandler {

        private final String prefix;

        private GreetingInvocationHandler(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(method.getName())) {
                    return "GreetingInvocationHandlerProxy";
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
            }
            return this.prefix + args[0];
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {

        private final List<String> requestedClassNames = new ArrayList<>();

        private TrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            this.requestedClassNames.add(name);
            return super.loadClass(name);
        }

        private List<String> requestedClassNames() {
            return this.requestedClassNames;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {

        private final String rejectedClassName;
        private final List<String> rejectedClassNames = new ArrayList<>();

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (this.rejectedClassName.equals(name)) {
                this.rejectedClassNames.add(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private List<String> rejectedClassNames() {
            return this.rejectedClassNames;
        }
    }
}
