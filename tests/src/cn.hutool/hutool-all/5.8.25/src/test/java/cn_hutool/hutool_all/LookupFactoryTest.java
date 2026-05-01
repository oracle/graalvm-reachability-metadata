/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.reflect.LookupFactory;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupFactoryTest {
    @Test
    public void usesJava8ConstructorFallbackWhenPrivateLookupInIsUnavailable() throws Throwable {
        VarHandle privateLookupInMethodHandle = lookupFactoryStaticField("privateLookupInMethod", Method.class);
        VarHandle java8LookupConstructorHandle = lookupFactoryStaticField("java8LookupConstructor", Constructor.class);
        Method originalPrivateLookupInMethod = (Method) privateLookupInMethodHandle.get();
        Constructor<?> originalJava8LookupConstructor = (Constructor<?>) java8LookupConstructorHandle.get();
        Constructor<MethodHandles.Lookup> fallbackConstructor = syntheticLookupConstructor();

        FallbackLookupInvocation.reset();
        try {
            privateLookupInMethodHandle.set(null);
            java8LookupConstructorHandle.set(fallbackConstructor);

            MethodHandles.Lookup lookup = LookupFactory.lookup(FallbackTarget.class);

            int allowedModes = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
                    | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
            assertThat(lookup.lookupClass()).isEqualTo(FallbackTarget.class);
            assertThat(FallbackLookupInvocation.invocationCount()).isEqualTo(1);
            assertThat(FallbackLookupInvocation.lastCallerClass()).isEqualTo(FallbackTarget.class);
            assertThat(FallbackLookupInvocation.lastAllowedModes()).isEqualTo(allowedModes);
        } finally {
            privateLookupInMethodHandle.set(originalPrivateLookupInMethod);
            java8LookupConstructorHandle.set(originalJava8LookupConstructor);
            FallbackLookupInvocation.reset();
        }
    }

    private static VarHandle lookupFactoryStaticField(String name, Class<?> type) throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LookupFactory.class, MethodHandles.lookup());
        return lookup.findStaticVarHandle(LookupFactory.class, name, type);
    }

    @SuppressWarnings("unchecked")
    private static Constructor<MethodHandles.Lookup> syntheticLookupConstructor() throws Throwable {
        MethodHandles.Lookup trustedLookup = trustedLookup();
        MethodHandle constructorFactory = trustedLookup.findConstructor(
                Constructor.class,
                MethodType.methodType(
                        void.class,
                        Class.class,
                        Class[].class,
                        Class[].class,
                        int.class,
                        int.class,
                        String.class,
                        byte[].class,
                        byte[].class));
        Constructor<MethodHandles.Lookup> constructor = (Constructor<MethodHandles.Lookup>) constructorFactory.invoke(
                MethodHandles.Lookup.class,
                new Class<?>[] {Class.class, int.class},
                new Class<?>[0],
                Modifier.PUBLIC,
                0,
                null,
                null,
                null);

        Class<?> constructorAccessorClass = Class.forName("jdk.internal.reflect.ConstructorAccessor");
        Object constructorAccessor = Proxy.newProxyInstance(
                Constructor.class.getClassLoader(),
                new Class<?>[] {constructorAccessorClass},
                lookupConstructorInvocationHandler());
        MethodHandle setConstructorAccessor = trustedLookup.findVirtual(
                Constructor.class,
                "setConstructorAccessor",
                MethodType.methodType(void.class, constructorAccessorClass));
        setConstructorAccessor.invoke(constructor, constructorAccessor);

        MethodHandle overrideSetter = trustedLookup.findSetter(AccessibleObject.class, "override", boolean.class);
        overrideSetter.invoke(constructor, true);
        return constructor;
    }

    private static InvocationHandler lookupConstructorInvocationHandler() {
        return (Object proxy, Method method, Object[] args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if ("newInstance".equals(method.getName())) {
                Object[] constructorArguments = (Object[]) args[0];
                Class<?> callerClass = (Class<?>) constructorArguments[0];
                int allowedModes = (Integer) constructorArguments[1];
                FallbackLookupInvocation.record(callerClass, allowedModes);
                return MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup());
            }
            throw new UnsupportedOperationException(method.toString());
        };
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        if ("toString".equals(method.getName())) {
            return LookupFactoryTest.class.getName() + " constructor accessor";
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return proxy == args[0];
        }
        throw new UnsupportedOperationException(method.toString());
    }

    private static MethodHandles.Lookup trustedLookup() throws Exception {
        Unsafe unsafe = unsafe();
        Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Object base = unsafe.staticFieldBase(implLookupField);
        long offset = unsafe.staticFieldOffset(implLookupField);
        return (MethodHandles.Lookup) unsafe.getObject(base, offset);
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    public static class FallbackLookupInvocation {
        private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();
        private static final AtomicReference<Class<?>> LAST_CALLER_CLASS = new AtomicReference<>();
        private static final AtomicInteger LAST_ALLOWED_MODES = new AtomicInteger();

        static void record(Class<?> callerClass, int allowedModes) {
            INVOCATION_COUNT.incrementAndGet();
            LAST_CALLER_CLASS.set(callerClass);
            LAST_ALLOWED_MODES.set(allowedModes);
        }

        static int invocationCount() {
            return INVOCATION_COUNT.get();
        }

        static Class<?> lastCallerClass() {
            return LAST_CALLER_CLASS.get();
        }

        static int lastAllowedModes() {
            return LAST_ALLOWED_MODES.get();
        }

        static void reset() {
            INVOCATION_COUNT.set(0);
            LAST_CALLER_CLASS.set(null);
            LAST_ALLOWED_MODES.set(0);
        }
    }

    public static class FallbackTarget {
    }
}
