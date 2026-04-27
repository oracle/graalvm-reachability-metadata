/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.rocksdb.NativeLibraryLoader;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeLibraryLoaderTest {
    private static final String PRIMARY_LIBRARY = "metadata-forge-missing-primary-rocksdbjni.jnilib";
    private static final String FALLBACK_LIBRARY = "metadata-forge-missing-fallback-rocksdbjni.jnilib";
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void loadLibraryFromJarToTempLooksUpFallbackResourceWhenPrimaryIsUnavailable() throws Throwable {
        NativeLibraryLoader loader = NativeLibraryLoader.getInstance();
        StaticFieldValue<String> primaryLibraryField = staticFieldValue("jniLibraryFileName");
        StaticFieldValue<String> fallbackLibraryField = staticFieldValue("fallbackJniLibraryFileName");

        primaryLibraryField.set(PRIMARY_LIBRARY);
        fallbackLibraryField.set(FALLBACK_LIBRARY);
        try {
            assertThatThrownBy(() -> invokeLoadLibraryFromJarToTemp(loader))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Neither " + PRIMARY_LIBRARY + " or " + FALLBACK_LIBRARY
                            + " were found inside the JAR, and there is no fallback.");
        } finally {
            fallbackLibraryField.restore();
            primaryLibraryField.restore();
        }
    }

    private static void invokeLoadLibraryFromJarToTemp(NativeLibraryLoader loader) throws Throwable {
        Method method = NativeLibraryLoader.class.getDeclaredMethod("loadLibraryFromJarToTemp", String.class);
        method.setAccessible(true);
        try {
            method.invoke(loader, (String) null);
        } catch (InvocationTargetException invocationTargetException) {
            throw invocationTargetException.getTargetException();
        }
    }

    private static StaticFieldValue<String> staticFieldValue(String name) throws NoSuchFieldException {
        Field field = NativeLibraryLoader.class.getDeclaredField(name);
        Object base = UNSAFE.staticFieldBase(field);
        long offset = UNSAFE.staticFieldOffset(field);
        return new StaticFieldValue<>(base, offset, String.class.cast(UNSAFE.getObject(base, offset)));
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return Unsafe.class.cast(field.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class StaticFieldValue<T> {
        private final Object base;
        private final long offset;
        private final T originalValue;

        private StaticFieldValue(Object base, long offset, T originalValue) {
            this.base = base;
            this.offset = offset;
            this.originalValue = originalValue;
        }

        private void set(T value) {
            UNSAFE.putObject(base, offset, value);
        }

        private void restore() {
            set(originalValue);
        }
    }
}
