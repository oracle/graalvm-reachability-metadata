/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jrockit.vm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.instantiator.jrockit.JRockitLegacyInstantiator;

public class JRockitLegacyInstantiatorTest {

    @Test
    void createsInstancesThroughJRockitMemSystemAdapter() throws Throwable {
        resetJRockitLegacyStaticState();
        MemSystem.reset();
        JRockitLegacyTarget.constructorCalls = 0;

        ObjectInstantiator instantiator = new JRockitLegacyInstantiator(JRockitLegacyTarget.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(JRockitLegacyTarget.class);
        Assertions.assertThat(MemSystem.requestedType()).isEqualTo(JRockitLegacyTarget.class);
        Assertions.assertThat(((JRockitLegacyTarget) instance).value).isEqualTo("created by MemSystem");
        Assertions.assertThat(JRockitLegacyTarget.constructorCalls).isEqualTo(1);
    }

    private static void resetJRockitLegacyStaticState() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(JRockitLegacyInstantiator.class, MethodHandles.lookup());
        clearStaticField(lookup, "safeAllocObjectMethod", Method.class);
        clearStaticField(lookup, "class$java$lang$Class", Class.class);
    }

    private static void clearStaticField(MethodHandles.Lookup lookup, String fieldName, Class<?> fieldType)
        throws NoSuchFieldException, IllegalAccessException {
        VarHandle field = lookup.findStaticVarHandle(JRockitLegacyInstantiator.class, fieldName, fieldType);
        field.set(null);
    }

    public static class JRockitLegacyTarget {
        static int constructorCalls;

        final String value;

        public JRockitLegacyTarget() {
            constructorCalls++;
            value = "created by MemSystem";
        }
    }
}

class MemSystem {
    private static Class<?> requestedType;

    static Object safeAllocObject(Class<?> type) {
        requestedType = type;
        if (type == JRockitLegacyInstantiatorTest.JRockitLegacyTarget.class) {
            return new JRockitLegacyInstantiatorTest.JRockitLegacyTarget();
        }
        throw new IllegalArgumentException("Unexpected type: " + type.getName());
    }

    static void reset() {
        requestedType = null;
    }

    static Class<?> requestedType() {
        return requestedType;
    }
}
