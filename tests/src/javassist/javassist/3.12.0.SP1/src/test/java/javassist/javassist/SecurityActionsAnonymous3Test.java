/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.Permission;

import javassist.util.proxy.FactoryHelper;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
public class SecurityActionsAnonymous3Test {
    @Test
    void getsDeclaredMethodWithSecurityManagerInstalled() throws Throwable {
        SecurityManager originalManager = System.getSecurityManager();
        SecurityManager manager = null;
        boolean installed = false;
        try {
            try {
                manager = new PermissiveSecurityManager();
                System.setSecurityManager(manager);
                installed = System.getSecurityManager() == manager;
            } catch (UnsupportedOperationException exception) {
                assertThat(exception).isInstanceOf(UnsupportedOperationException.class);
            }

            Method method = getDeclaredMethod(MethodTarget.class, "message",
                    new Class<?>[] {String.class });

            assertThat(method.getDeclaringClass()).isEqualTo(MethodTarget.class);
            assertThat(method.getParameterTypes()).containsExactly(String.class);
            assertThat(method.getReturnType()).isEqualTo(String.class);
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
    }

    @Test
    void factoryHelperFindsClassLoaderDefineClassMethodsWithSecurityManagerInstalled() throws Exception {
        SecurityManager originalManager = System.getSecurityManager();
        SecurityManager manager = null;
        boolean installed = false;
        try {
            try {
                manager = new PermissiveSecurityManager();
                System.setSecurityManager(manager);
                installed = System.getSecurityManager() == manager;
            } catch (UnsupportedOperationException exception) {
                assertThat(exception).isInstanceOf(UnsupportedOperationException.class);
            }

            try (ChildFirstJavassistLoader loader = new ChildFirstJavassistLoader(
                    SecurityActionsAnonymous3Test.class.getClassLoader())) {
                Class<?> initializer = Class.forName(
                        IsolatedFactoryHelperInitializer.class.getName(), true, loader);

                assertThat(initializer.getName())
                        .isEqualTo(IsolatedFactoryHelperInitializer.class.getName());
            } catch (RuntimeException exception) {
                if (!hasUnsupportedFeatureError(exception)) {
                    throw exception;
                }
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            if (installed) {
                System.setSecurityManager(originalManager);
            }
        }
    }

    private static Method getDeclaredMethod(
            Class<?> targetClass, String name, Class<?>[] parameterTypes) throws Throwable {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                securityActions, MethodHandles.lookup());
        MethodHandle getDeclaredMethod = lookup.findStatic(
                securityActions, "getDeclaredMethod",
                MethodType.methodType(Method.class, Class.class, String.class, Class[].class));
        return (Method) getDeclaredMethod.invoke(targetClass, name, parameterTypes);
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
            // Permit all operations while the Javassist security-manager branch is exercised.
        }
    }

    public static final class MethodTarget {
        public String message(String value) {
            return value;
        }
    }

    public static final class IsolatedFactoryHelperInitializer {
        static final int INTEGER_INDEX = FactoryHelper.typeIndex(int.class);

        private IsolatedFactoryHelperInitializer() {
        }
    }

    private static final class ChildFirstJavassistLoader extends ClassLoader implements AutoCloseable {
        private final ClassLoader resourceLoader;

        private ChildFirstJavassistLoader(ClassLoader resourceLoader) {
            super(resourceLoader);
            this.resourceLoader = resourceLoader;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null && shouldLoadChildFirst(name)) {
                try {
                    loadedClass = findClass(name);
                } catch (ClassNotFoundException exception) {
                    loadedClass = super.loadClass(name, false);
                }
            }
            if (loadedClass == null) {
                loadedClass = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!shouldLoadChildFirst(name)) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = readClassBytes(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream input = resourceLoader.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toByteArray();
            } catch (IOException exception) {
                ClassNotFoundException classNotFoundException = new ClassNotFoundException(name);
                classNotFoundException.initCause(exception);
                throw classNotFoundException;
            }
        }

        private static boolean shouldLoadChildFirst(String name) {
            return name.equals(IsolatedFactoryHelperInitializer.class.getName())
                    || name.equals(FactoryHelper.class.getName())
                    || name.equals("javassist.util.proxy.SecurityActions")
                    || name.equals("javassist.util.proxy.SecurityActions$3");
        }

        @Override
        public void close() {
        }
    }
}
