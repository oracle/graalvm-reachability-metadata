/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.sun.Sun13Instantiator;
import org.objenesis.instantiator.sun.Sun13InstantiatorBase;

public class Sun13InstantiatorTest {

    @Test
    void delegatesInstanceCreationToConfiguredSun13AllocationHook()
        throws ReflectiveOperationException {
        ConstructorBypassedType preparedInstance = new ConstructorBypassedType();
        ConstructorBypassedType.constructorCalls.set(0);
        Sun13RuntimeSupport.prepare(preparedInstance);
        resetSun13ClassLiteralCache();
        Method allocateNewObjectMethod = Sun13RuntimeSupport.class.getDeclaredMethod(
            "allocateNewObject",
            Class.class,
            Class.class
        );
        Sun13StateAccess.configure(allocateNewObjectMethod);

        Sun13Instantiator instantiator = new Sun13Instantiator(ConstructorBypassedType.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isSameAs(preparedInstance);
        Assertions.assertThat(Sun13RuntimeSupport.requestedType())
            .isEqualTo(ConstructorBypassedType.class);
        Assertions.assertThat(Sun13RuntimeSupport.requestedParentType())
            .isEqualTo(Object.class);
        Assertions.assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static void resetSun13ClassLiteralCache() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup =
            MethodHandles.privateLookupIn(Sun13Instantiator.class, MethodHandles.lookup());
        VarHandle field = lookup.findStaticVarHandle(
            Sun13Instantiator.class,
            "class$java$lang$Object",
            Class.class
        );
        field.set(null);
    }

    public static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
        }
    }

    public static final class Sun13RuntimeSupport {
        private static Object preparedInstance;
        private static Class<?> requestedType;
        private static Class<?> requestedParentType;

        private Sun13RuntimeSupport() {
        }

        static void prepare(Object instance) {
            preparedInstance = instance;
            requestedType = null;
            requestedParentType = null;
        }

        public static Object allocateNewObject(Class<?> type, Class<?> parentType) {
            requestedType = type;
            requestedParentType = parentType;
            return preparedInstance;
        }

        static Class<?> requestedType() {
            return requestedType;
        }

        static Class<?> requestedParentType() {
            return requestedParentType;
        }
    }

    private abstract static class Sun13StateAccess extends Sun13InstantiatorBase {
        private Sun13StateAccess(Class<?> type) {
            super(type);
        }

        static void configure(Method method) {
            allocateNewObjectMethod = method;
        }
    }
}
