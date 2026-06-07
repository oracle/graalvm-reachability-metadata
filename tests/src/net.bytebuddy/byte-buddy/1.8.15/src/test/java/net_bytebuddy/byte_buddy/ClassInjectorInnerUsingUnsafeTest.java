/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingUnsafeTest {
    @Test
    void injectReturnsAlreadyLoadedTypeFromTargetClassLoader() throws Exception {
        Field dispatcherField = null;
        UnsafeStaticFieldAccess unsafe = null;
        Object originalDispatcher = null;
        try {
            dispatcherField = ClassInjector.UsingUnsafe.class.getDeclaredField("DISPATCHER");
            unsafe = UnsafeStaticFieldAccess.locate();
            originalDispatcher = unsafe.getStatic(dispatcherField);
            unsafe.putStatic(dispatcherField, UsingUnsafeAccess.availableDispatcher());

            TypeDescription typeDescription = TypeDescription.ForLoadedType.of(
                    ClassInjectorInnerUsingUnsafeTest.class);
            ClassInjector injector = new ClassInjector.UsingUnsafe(
                    ClassInjectorInnerUsingUnsafeTest.class.getClassLoader());
            Map<TypeDescription, Class<?>> loaded = injector.inject(
                    Collections.singletonMap(typeDescription, new byte[0]));

            assertThat(loaded).containsEntry(typeDescription, ClassInjectorInnerUsingUnsafeTest.class);
        } catch (IllegalStateException exception) {
            if (!isUnsupportedNativeImageOperation(exception) && !isUnsafeUnavailable(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (unsafe != null && dispatcherField != null && originalDispatcher != null) {
                unsafe.putStatic(dispatcherField, originalDispatcher);
            }
        }
    }

    private static boolean isUnsupportedNativeImageOperation(IllegalStateException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause);
    }

    private static boolean isUnsafeUnavailable(IllegalStateException exception) {
        return !ClassInjector.UsingUnsafe.isAvailable()
                && exception.getMessage() != null
                && exception.getMessage().contains("sun.misc.Unsafe");
    }

    private static class UsingUnsafeAccess extends ClassInjector.UsingUnsafe {
        UsingUnsafeAccess() {
            super(ClassInjectorInnerUsingUnsafeTest.class.getClassLoader());
        }

        static Object availableDispatcher() {
            return new AvailableDispatcher();
        }

        private static class AvailableDispatcher implements Dispatcher.Initializable {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Dispatcher initialize() {
                return new NoOpDispatcher();
            }
        }

        private static class NoOpDispatcher implements Dispatcher {
            @Override
            public Class<?> defineClass(ClassLoader classLoader,
                                        String name,
                                        byte[] binaryRepresentation,
                                        ProtectionDomain protectionDomain) {
                throw new AssertionError("Already loaded types must be resolved before defining a class");
            }
        }
    }

    private static class UnsafeStaticFieldAccess {
        private final Object unsafe;
        private final Method staticFieldBase;
        private final Method staticFieldOffset;
        private final Method getObject;
        private final Method putObjectVolatile;

        UnsafeStaticFieldAccess(Object unsafe,
                                Method staticFieldBase,
                                Method staticFieldOffset,
                                Method getObject,
                                Method putObjectVolatile) {
            this.unsafe = unsafe;
            this.staticFieldBase = staticFieldBase;
            this.staticFieldOffset = staticFieldOffset;
            this.getObject = getObject;
            this.putObjectVolatile = putObjectVolatile;
        }

        static UnsafeStaticFieldAccess locate() throws Exception {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return new UnsafeStaticFieldAccess(
                    theUnsafe.get(null),
                    unsafeType.getMethod("staticFieldBase", Field.class),
                    unsafeType.getMethod("staticFieldOffset", Field.class),
                    unsafeType.getMethod("getObject", Object.class, long.class),
                    unsafeType.getMethod("putObjectVolatile", Object.class, long.class, Object.class));
        }

        Object getStatic(Field field) throws Exception {
            return getObject.invoke(unsafe, staticFieldBase(field), staticFieldOffset(field));
        }

        void putStatic(Field field, Object value) throws Exception {
            putObjectVolatile.invoke(unsafe, staticFieldBase(field), staticFieldOffset(field), value);
        }

        private Object staticFieldBase(Field field) throws Exception {
            return staticFieldBase.invoke(unsafe, field);
        }

        private long staticFieldOffset(Field field) throws Exception {
            return (Long) staticFieldOffset.invoke(unsafe, field);
        }
    }
}
