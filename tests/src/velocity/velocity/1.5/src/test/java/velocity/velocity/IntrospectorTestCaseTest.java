/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCaseTest {
    @Test
    void resolvesPrimitiveMethodsAndRejectsNonPublicMethods() throws Exception {
        final Introspector introspector = new Introspector(new Log(new NullLogChute()));
        final MethodProvider provider = new MethodProvider();

        assertPrimitiveMethod(introspector, provider, "booleanMethod", Boolean.TRUE, "boolean");
        assertPrimitiveMethod(introspector, provider, "byteMethod", Byte.valueOf((byte) 1), "byte");
        assertPrimitiveMethod(
                introspector, provider, "characterMethod", Character.valueOf('a'), "character");
        assertPrimitiveMethod(
                introspector, provider, "doubleMethod", Double.valueOf(1.0d), "double");
        assertPrimitiveMethod(introspector, provider, "floatMethod", Float.valueOf(1.0f), "float");
        assertPrimitiveMethod(
                introspector, provider, "integerMethod", Integer.valueOf(1), "integer");
        assertPrimitiveMethod(introspector, provider, "longMethod", Long.valueOf(1L), "long");
        assertPrimitiveMethod(
                introspector, provider, "shortMethod", Short.valueOf((short) 1), "short");

        assertThat(introspector.getMethod(
                MethodProvider.class, "untouchable", new Object[0])).isNull();
        assertThat(introspector.getMethod(
                MethodProvider.class, "reallyUntouchable", new Object[0])).isNull();
    }

    private static void assertPrimitiveMethod(
            final Introspector introspector,
            final MethodProvider provider,
            final String methodName,
            final Object parameter,
            final String expectedResult) throws Exception {
        final Method method = introspector.getMethod(
                MethodProvider.class, methodName, new Object[] {parameter});

        assertThat(method).isNotNull();
        assertThat(method.invoke(provider, parameter)).isEqualTo(expectedResult);
    }

    public static class MethodProvider {
        public String booleanMethod(final boolean value) {
            return "boolean";
        }

        public String byteMethod(final byte value) {
            return "byte";
        }

        public String characterMethod(final char value) {
            return "character";
        }

        public String doubleMethod(final double value) {
            return "double";
        }

        public String floatMethod(final float value) {
            return "float";
        }

        public String integerMethod(final int value) {
            return "integer";
        }

        public String longMethod(final long value) {
            return "long";
        }

        public String shortMethod(final short value) {
            return "short";
        }

        String untouchable() {
            return "hidden";
        }

        private String reallyUntouchable() {
            return "hidden";
        }
    }
}
