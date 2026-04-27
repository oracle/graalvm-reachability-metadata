/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jrockit.vm;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.instantiator.jrockit.JRockitLegacyInstantiator;

public class JRockitLegacyInstantiatorTest {

    @Test
    void createsInstancesThroughJRockitMemSystemAdapter() {
        MemSystem.reset();
        JRockitLegacyTarget.constructorCalls = 0;

        ObjectInstantiator instantiator = new JRockitLegacyInstantiator(JRockitLegacyTarget.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(JRockitLegacyTarget.class);
        Assertions.assertThat(MemSystem.requestedType()).isEqualTo(JRockitLegacyTarget.class);
        Assertions.assertThat(((JRockitLegacyTarget) instance).value).isEqualTo("created by MemSystem");
        Assertions.assertThat(JRockitLegacyTarget.constructorCalls).isEqualTo(1);
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
