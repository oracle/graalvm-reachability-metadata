/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Paths;

import org.apache.arrow.memory.util.MemoryUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUtilTest {
    private static final String MEMORY_UTIL_CLASS_NAME = "org.apache.arrow.memory.util.MemoryUtil";
    private static final String MEMORY_UTIL_CLASS_RESOURCE = "org/apache/arrow/memory/util/MemoryUtil.class";

    @Test
    void directBufferConstructorPathsAreExercised() throws ReflectiveOperationException {
        exerciseStaticInitializerConstructorProbe();

        long address = MemoryUtil.UNSAFE.allocateMemory(8);
        try {
            MemoryUtil.UNSAFE.setMemory(address, 8, (byte) 0);

            ByteBuffer buffer = directBufferOrNull(address, 8);

            if (buffer == null) {
                ByteBuffer ordinaryDirectBuffer = ByteBuffer.allocateDirect(1);
                assertThat(MemoryUtil.getByteBufferAddress(ordinaryDirectBuffer)).isNotZero();
            } else {
                assertThat(buffer.isDirect()).isTrue();
                assertThat(buffer.capacity()).isEqualTo(8);
                assertThat(MemoryUtil.getByteBufferAddress(buffer)).isEqualTo(address);

                buffer.put(0, (byte) 42);
                assertThat(MemoryUtil.UNSAFE.getByte(address)).isEqualTo((byte) 42);

                MemoryUtil.UNSAFE.putByte(address + 1, (byte) 7);
                assertThat(buffer.get(1)).isEqualTo((byte) 7);
            }
        } finally {
            MemoryUtil.UNSAFE.freeMemory(address);
        }
    }

    private static void exerciseStaticInitializerConstructorProbe() throws ReflectiveOperationException {
        try (ChildFirstMemoryUtilClassLoader classLoader = new ChildFirstMemoryUtilClassLoader(
                new URL[] { arrowMemoryCoreJarUrl() },
                MemoryUtilTest.class.getClassLoader())) {
            Unsafe unsafe = unsafe();
            Class<?> originalIntegerType = replaceIntegerType(unsafe, Long.TYPE);
            try {
                Class.forName(MEMORY_UTIL_CLASS_NAME, true, classLoader);
            } catch (ExceptionInInitializerError initializerError) {
                assertThat(causeOfType(initializerError, IllegalArgumentException.class)).isNotNull();
            } finally {
                replaceIntegerType(unsafe, originalIntegerType);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            throw new ReflectiveOperationException("Unable to exercise isolated MemoryUtil initialization", exception);
        }
    }

    private static Throwable causeOfType(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static URL arrowMemoryCoreJarUrl() throws Exception {
        URL codeSource = MemoryUtil.class.getProtectionDomain().getCodeSource().getLocation();
        if (codeSource != null) {
            return codeSource;
        }

        URL resource = MemoryUtilTest.class.getClassLoader().getResource(MEMORY_UTIL_CLASS_RESOURCE);
        if (resource != null) {
            String resourceUrl = resource.toExternalForm();
            if (resourceUrl.startsWith("jar:")) {
                return new URL(resourceUrl.substring("jar:".length(), resourceUrl.indexOf("!/")));
            }
            return new URL(resourceUrl.substring(0, resourceUrl.length() - MEMORY_UTIL_CLASS_RESOURCE.length()));
        }

        for (String classPathEntry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (classPathEntry.contains("arrow-memory-core") && classPathEntry.endsWith(".jar")) {
                return Paths.get(classPathEntry).toUri().toURL();
            }
        }
        throw new IllegalStateException("Unable to locate arrow-memory-core on the test class path");
    }

    private static ByteBuffer directBufferOrNull(long address, int capacity) throws ReflectiveOperationException {
        try {
            return MemoryUtil.directBuffer(address, capacity);
        } catch (UnsupportedOperationException unsupported) {
            assertThat(unsupported).hasMessageContaining("DirectByteBuffer.<init>(long, int) not available");
            installCompatibleDirectBufferConstructor();
            try {
                return MemoryUtil.directBuffer(address, capacity);
            } catch (Error error) {
                assertThat(causeOfType(error, IllegalArgumentException.class)).isNotNull();
                return null;
            }
        }
    }

    private static void installCompatibleDirectBufferConstructor() throws ReflectiveOperationException {
        Class<?> directBufferClass = ByteBuffer.allocateDirect(0).getClass();
        Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, long.class);
        constructor.setAccessible(true);

        Field directBufferConstructor = MemoryUtil.class.getDeclaredField("DIRECT_BUFFER_CONSTRUCTOR");
        Object staticFieldBase = MemoryUtil.UNSAFE.staticFieldBase(directBufferConstructor);
        long staticFieldOffset = MemoryUtil.UNSAFE.staticFieldOffset(directBufferConstructor);
        MemoryUtil.UNSAFE.putObject(staticFieldBase, staticFieldOffset, constructor);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static Class<?> replaceIntegerType(Unsafe unsafe, Class<?> replacement)
            throws ReflectiveOperationException {
        Field integerType = Integer.class.getDeclaredField("TYPE");
        Object staticFieldBase = unsafe.staticFieldBase(integerType);
        long staticFieldOffset = unsafe.staticFieldOffset(integerType);
        Class<?> previousType = (Class<?>) unsafe.getObject(staticFieldBase, staticFieldOffset);
        unsafe.putObject(staticFieldBase, staticFieldOffset, replacement);
        return previousType;
    }

    private static final class ChildFirstMemoryUtilClassLoader extends URLClassLoader {
        private ChildFirstMemoryUtilClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith(MEMORY_UTIL_CLASS_NAME)) {
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
    }
}
