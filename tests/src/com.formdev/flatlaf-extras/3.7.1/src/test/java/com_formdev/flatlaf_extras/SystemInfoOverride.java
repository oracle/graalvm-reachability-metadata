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
    private final StaticBooleanField macOs;
    private final StaticBooleanField java9OrLater;

    private SystemInfoOverride(StaticBooleanField macOs, StaticBooleanField java9OrLater) {
        this.macOs = macOs;
        this.java9OrLater = java9OrLater;
    }

    static SystemInfoOverride legacyMacJava8() throws ReflectiveOperationException {
        Class.forName(SystemInfo.class.getName(), true, SystemInfo.class.getClassLoader());

        Unsafe unsafe = unsafe();
        StaticBooleanField macOs = staticBooleanField(unsafe, "isMacOS");
        StaticBooleanField java9OrLater = staticBooleanField(unsafe, "isJava_9_orLater");
        macOs.set(true);
        java9OrLater.set(false);
        return new SystemInfoOverride(macOs, java9OrLater);
    }

    @Override
    public void close() {
        java9OrLater.restore();
        macOs.restore();
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
