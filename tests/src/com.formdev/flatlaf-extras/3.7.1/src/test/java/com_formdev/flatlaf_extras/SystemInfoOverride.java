/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import com.formdev.flatlaf.util.SystemInfo;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

final class SystemInfoOverride implements AutoCloseable {
    private final StaticBooleanField[] fields;

    private SystemInfoOverride(StaticBooleanField... fields) {
        this.fields = fields;
    }

    static SystemInfoOverride legacyMacJava8() throws ReflectiveOperationException {
        Unsafe unsafe = initializedUnsafe();
        StaticBooleanField macOs = staticBooleanField(unsafe, "isMacOS");
        StaticBooleanField java9OrLater = staticBooleanField(unsafe, "isJava_9_orLater");
        macOs.set(true);
        java9OrLater.set(false);
        return new SystemInfoOverride(macOs, java9OrLater);
    }

    static SystemInfoOverride java9OrLater(boolean value) throws ReflectiveOperationException {
        Unsafe unsafe = initializedUnsafe();
        StaticBooleanField java9OrLater = staticBooleanField(unsafe, "isJava_9_orLater");
        java9OrLater.set(value);
        return new SystemInfoOverride(java9OrLater);
    }

    @Override
    public void close() {
        for (int i = fields.length - 1; i >= 0; i--) {
            fields[i].restore();
        }
    }

    private static Unsafe initializedUnsafe() throws ReflectiveOperationException {
        Class.forName(SystemInfo.class.getName(), true, SystemInfo.class.getClassLoader());
        return unsafe();
    }

    private static StaticBooleanField staticBooleanField(Unsafe unsafe, String fieldName) throws NoSuchFieldException {
        Field field = SystemInfo.class.getDeclaredField(fieldName);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        return new StaticBooleanField(unsafe, base, offset, unsafe.getBooleanVolatile(base, offset));
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return Unsafe.class.cast(field.get(null));
    }

    private static final class StaticBooleanField {
        private final Unsafe unsafe;
        private final Object base;
        private final long offset;
        private final boolean originalValue;

        private StaticBooleanField(Unsafe unsafe, Object base, long offset, boolean originalValue) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
            this.originalValue = originalValue;
        }

        private void set(boolean value) {
            unsafe.putBooleanVolatile(base, offset, value);
        }

        private void restore() {
            set(originalValue);
        }
    }
}
