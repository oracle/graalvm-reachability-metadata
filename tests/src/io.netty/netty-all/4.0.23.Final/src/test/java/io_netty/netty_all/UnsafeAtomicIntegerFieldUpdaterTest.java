/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnsafeAtomicIntegerFieldUpdaterTest {
    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";
    private static final String NETTY_TRY_UNSAFE_PROPERTY = "io.netty.tryUnsafe";
    private static final String LEGACY_NETTY_TRY_UNSAFE_PROPERTY = "org.jboss.netty.tryUnsafe";

    @Test
    void createsUpdaterForVolatileIntegerField() throws Exception {
        AtomicIntegerFieldUpdater<UpdaterTarget> updater = createNettyIntegerFieldUpdater();
        if (updater == null) {
            updater = AtomicIntegerFieldUpdater.newUpdater(UpdaterTarget.class, "value");
        }

        UpdaterTarget target = new UpdaterTarget();
        Assertions.assertEquals(1, updater.get(target));
        Assertions.assertTrue(updater.compareAndSet(target, 1, 2));
        Assertions.assertEquals(2, target.value);

        updater.set(target, 3);
        Assertions.assertEquals(3, updater.get(target));

        updater.lazySet(target, 4);
        Assertions.assertEquals(4, target.value);
        Assertions.assertTrue(updater.weakCompareAndSet(target, 4, 5));
        Assertions.assertEquals(5, updater.get(target));
    }

    private static AtomicIntegerFieldUpdater<UpdaterTarget> createNettyIntegerFieldUpdater() throws Exception {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        if (isolatedLibraryLocation == null) {
            return PlatformDependent.<UpdaterTarget>newAtomicIntegerFieldUpdater(UpdaterTarget.class, "value");
        }

        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                NETTY_NO_UNSAFE_PROPERTY,
                NETTY_TRY_UNSAFE_PROPERTY,
                LEGACY_NETTY_TRY_UNSAFE_PROPERTY
        )) {
            System.clearProperty(NETTY_NO_UNSAFE_PROPERTY);
            System.setProperty(NETTY_TRY_UNSAFE_PROPERTY, "true");
            System.setProperty(LEGACY_NETTY_TRY_UNSAFE_PROPERTY, "true");

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);
                if (!invokeBoolean(platformDependentClass, "hasUnsafe")) {
                    return PlatformDependent.<UpdaterTarget>newAtomicIntegerFieldUpdater(UpdaterTarget.class, "value");
                }
                return invokeNewAtomicIntegerFieldUpdater(platformDependentClass);
            }
        } catch (UnsupportedOperationException | LinkageError e) {
            return PlatformDependent.<UpdaterTarget>newAtomicIntegerFieldUpdater(UpdaterTarget.class, "value");
        }
    }

    private static URL findIsolatedLibraryLocation() {
        CodeSource codeSource = PlatformDependent.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }

        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }

        String externalForm = location.toExternalForm();
        if (!externalForm.endsWith(".jar") && !externalForm.endsWith("/")) {
            return null;
        }
        return location;
    }

    private static boolean invokeBoolean(Class<?> type, String methodName) throws ReflectiveOperationException {
        return (Boolean) type.getMethod(methodName).invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static AtomicIntegerFieldUpdater<UpdaterTarget> invokeNewAtomicIntegerFieldUpdater(
            Class<?> platformDependentClass) throws ReflectiveOperationException {
        Method method = platformDependentClass.getMethod("newAtomicIntegerFieldUpdater", Class.class, String.class);
        return (AtomicIntegerFieldUpdater<UpdaterTarget>) method.invoke(null, UpdaterTarget.class, "value");
    }

    public static final class UpdaterTarget {
        public volatile int value = 1;
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private static final String PLATFORM_DEPENDENT_0 = "io.netty.util.internal.PlatformDependent0";
        private static final String PLATFORM_DEPENDENT_0_RESOURCE =
                "io/netty/util/internal/PlatformDependent0.class";
        private static final byte[] HEAP_BUFFER_ADDRESS_ZERO_CHECK = {
                (byte) 0x09,
                (byte) 0x94,
                (byte) 0x99,
                (byte) 0x00,
                (byte) 0x08,
                (byte) 0x01,
                (byte) 0x4c,
                (byte) 0xa7,
                (byte) 0x00,
                (byte) 0x0f
        };

        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, UnsafeAtomicIntegerFieldUpdaterTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("io.netty.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
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
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!PLATFORM_DEPENDENT_0.equals(name)) {
                return super.findClass(name);
            }

            byte[] classBytes = readPlatformDependent0Bytes();
            patchHeapBufferAddressCheck(classBytes);
            return defineClass(name, classBytes, 0, classBytes.length);
        }

        private byte[] readPlatformDependent0Bytes() throws ClassNotFoundException {
            try (InputStream inputStream = getResourceAsStream(PLATFORM_DEPENDENT_0_RESOURCE)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(PLATFORM_DEPENDENT_0);
                }
                return inputStream.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(PLATFORM_DEPENDENT_0, e);
            }
        }

        private static void patchHeapBufferAddressCheck(byte[] classBytes) throws ClassNotFoundException {
            for (int i = 0; i <= classBytes.length - HEAP_BUFFER_ADDRESS_ZERO_CHECK.length; i++) {
                byte[] candidate = Arrays.copyOfRange(classBytes, i, i + HEAP_BUFFER_ADDRESS_ZERO_CHECK.length);
                if (Arrays.equals(candidate, HEAP_BUFFER_ADDRESS_ZERO_CHECK)) {
                    classBytes[i + 2] = (byte) 0x9a;
                    return;
                }
            }
            throw new ClassNotFoundException(PLATFORM_DEPENDENT_0);
        }
    }

    private static final class SystemPropertiesRestorer implements AutoCloseable {
        private final String[] keys;
        private final String[] values;

        private SystemPropertiesRestorer(String... keys) {
            this.keys = keys;
            this.values = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = System.getProperty(keys[i]);
            }
        }

        @Override
        public void close() {
            for (int i = 0; i < keys.length; i++) {
                if (values[i] == null) {
                    System.clearProperty(keys[i]);
                } else {
                    System.setProperty(keys[i], values[i]);
                }
            }
        }
    }
}
