/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeAtomicReferenceFieldUpdaterTest {
    @Test
    void createsOptimizedReferenceFieldUpdaterForVolatileField() throws Exception {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        if (isolatedLibraryLocation != null) {
            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                AtomicReferenceFieldUpdater<Holder, String> updater;
                try (HeapByteBufferArrayBaseOffsetRestorer ignored =
                             HeapByteBufferArrayBaseOffsetRestorer.zeroOffset()) {
                    Class<?> platformDependentClass = Class.forName(
                            "io.netty.util.internal.PlatformDependent",
                            true,
                            classLoader
                    );
                    updater = newAtomicReferenceFieldUpdater(platformDependentClass);
                }
                assertOptimizedUpdaterBehavior(updater);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
                return;
            }
            return;
        }

        AtomicReferenceFieldUpdater<Holder, String> updater;
        try (HeapByteBufferArrayBaseOffsetRestorer ignored = HeapByteBufferArrayBaseOffsetRestorer.zeroOffset()) {
            updater = PlatformDependent.newAtomicReferenceFieldUpdater(Holder.class, "value");
        }
        if (updater != null) {
            assertOptimizedUpdaterBehavior(updater);
        } else {
            assertThat(PlatformDependent.hasUnsafe()).isFalse();
        }
    }

    private static AtomicReferenceFieldUpdater<Holder, String> newAtomicReferenceFieldUpdater(
            Class<?> platformDependentClass
    ) throws ReflectiveOperationException {
        Method method = platformDependentClass.getMethod("newAtomicReferenceFieldUpdater", Class.class, String.class);
        @SuppressWarnings("unchecked")
        AtomicReferenceFieldUpdater<Holder, String> updater =
                (AtomicReferenceFieldUpdater<Holder, String>) method.invoke(null, Holder.class, "value");
        return updater;
    }

    private static void assertOptimizedUpdaterBehavior(AtomicReferenceFieldUpdater<Holder, String> updater) {
        assertThat(updater).isNotNull();
        assertThat(updater.getClass().getName()).isEqualTo("io.netty.util.internal.UnsafeAtomicReferenceFieldUpdater");

        Holder holder = new Holder();
        assertThat(updater.get(holder)).isEqualTo("initial");

        updater.set(holder, "set");
        assertThat(holder.value).isEqualTo("set");
        assertThat(updater.compareAndSet(holder, "set", "compareAndSet")).isTrue();
        assertThat(updater.get(holder)).isEqualTo("compareAndSet");
        assertThat(updater.compareAndSet(holder, "set", "unexpected")).isFalse();
        assertThat(updater.get(holder)).isEqualTo("compareAndSet");

        updater.lazySet(holder, "lazySet");
        assertThat(updater.get(holder)).isEqualTo("lazySet");
        assertThat(updater.weakCompareAndSet(holder, "lazySet", "weakCompareAndSet")).isTrue();
        assertThat(holder.value).isEqualTo("weakCompareAndSet");
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
        if (!externalForm.contains("netty-all") && !externalForm.contains("netty_all")) {
            return null;
        }
        return location;
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, UnsafeAtomicReferenceFieldUpdaterTest.class.getClassLoader());
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
    }

    private static final class HeapByteBufferArrayBaseOffsetRestorer implements AutoCloseable {
        private final Unsafe unsafe;
        private final Object fieldBase;
        private final long fieldOffset;
        private final long originalValue;

        private HeapByteBufferArrayBaseOffsetRestorer(
                Unsafe unsafe,
                Object fieldBase,
                long fieldOffset,
                long originalValue
        ) {
            this.unsafe = unsafe;
            this.fieldBase = fieldBase;
            this.fieldOffset = fieldOffset;
            this.originalValue = originalValue;
        }

        private static HeapByteBufferArrayBaseOffsetRestorer zeroOffset() {
            try {
                Unsafe unsafe = getUnsafe();
                Class<?> heapByteBufferClass = Class.forName("java.nio.HeapByteBuffer", true, null);
                Field arrayBaseOffsetField = heapByteBufferClass.getDeclaredField("ARRAY_BASE_OFFSET");
                Object fieldBase = unsafe.staticFieldBase(arrayBaseOffsetField);
                long fieldOffset = unsafe.staticFieldOffset(arrayBaseOffsetField);
                long originalValue = unsafe.getLong(fieldBase, fieldOffset);
                unsafe.putLong(fieldBase, fieldOffset, 0L);
                return new HeapByteBufferArrayBaseOffsetRestorer(unsafe, fieldBase, fieldOffset, originalValue);
            } catch (Throwable ignored) {
                return new HeapByteBufferArrayBaseOffsetRestorer(null, null, 0L, 0L);
            }
        }

        private static Unsafe getUnsafe() throws ReflectiveOperationException {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }

        @Override
        public void close() {
            if (unsafe != null) {
                unsafe.putLong(fieldBase, fieldOffset, originalValue);
            }
        }
    }

    private static final class Holder {
        private volatile String value = "initial";
    }
}
