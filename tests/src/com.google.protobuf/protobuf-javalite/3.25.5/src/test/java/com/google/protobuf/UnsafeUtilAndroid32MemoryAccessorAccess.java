/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class UnsafeUtilAndroid32MemoryAccessorAccess {
    private static final String ANDROID32_MEMORY_ACCESSOR_CLASS_NAME =
            "com.google.protobuf.UnsafeUtil$Android32MemoryAccessor";

    private UnsafeUtilAndroid32MemoryAccessorAccess() {
    }

    public static MapEntryLite<String, Value> loadMapDefaultEntry() {
        @SuppressWarnings("unchecked")
        MapEntryLite<String, Value> defaultEntry =
                (MapEntryLite<String, Value>)
                        SchemaUtil.getMapDefaultEntry(MapEntryContainer.class, "fields");
        return defaultEntry;
    }

    public static MapEntryLite<String, Value> loadMapDefaultEntryThroughAndroid32Accessor() {
        Field defaultEntryField = fieldOrThrow(MapEntryContainer.FieldsDefaultEntryHolder.class, "defaultEntry");
        Object accessor = newAndroid32MemoryAccessor();
        Method getStaticObject = methodOrThrow(accessor.getClass(), "getStaticObject", Field.class);
        @SuppressWarnings("unchecked")
        MapEntryLite<String, Value> defaultEntry =
                (MapEntryLite<String, Value>) invokeOrThrow(getStaticObject, accessor, defaultEntryField);
        return defaultEntry;
    }

    private static Field fieldOrThrow(Class<?> declaringClass, String fieldName) {
        try {
            return declaringClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Missing field " + fieldName + " on " + declaringClass.getName(), e);
        }
    }

    private static Object newAndroid32MemoryAccessor() {
        try {
            Class<?> accessorClass = Class.forName(ANDROID32_MEMORY_ACCESSOR_CLASS_NAME);
            Constructor<?> constructor = accessorClass.getDeclaredConstructor(sun.misc.Unsafe.class);
            constructor.setAccessible(true);
            return constructor.newInstance(UnsafeUtil.getUnsafe());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | InvocationTargetException | NoSuchMethodException e) {
            throw new AssertionError("Failed to create Android32 memory accessor", e);
        }
    }

    private static Method methodOrThrow(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Missing method " + methodName + " on " + declaringClass.getName(), e);
        }
    }

    private static Object invokeOrThrow(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Failed to invoke " + method.getName(), e);
        }
    }

    public static final class MapEntryContainer {
        private MapEntryContainer() {
        }

        public static final class FieldsDefaultEntryHolder {
            public static final MapEntryLite<String, Value> defaultEntry =
                    MapEntryLite.newDefaultInstance(
                            WireFormat.FieldType.STRING,
                            "",
                            WireFormat.FieldType.MESSAGE,
                            Value.getDefaultInstance());

            private FieldsDefaultEntryHolder() {
            }
        }
    }
}
