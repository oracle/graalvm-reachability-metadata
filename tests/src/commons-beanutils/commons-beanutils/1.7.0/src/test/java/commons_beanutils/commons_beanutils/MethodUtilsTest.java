/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.MethodUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodUtilsTest {
    @Test
    void invokesExactAndAssignmentCompatibleMethods() throws Exception {
        Operations operations = new Operations();

        assertEquals("hello Ada", MethodUtils.invokeMethod(operations, "greet", "Ada"));
        assertEquals(12, MethodUtils.invokeMethod(operations, "doubleValue", Integer.valueOf(6)));
        assertEquals(9, MethodUtils.invokeExactMethod(operations, "add",
                new Object[]{4, 5}, new Class<?>[]{int.class, int.class}));
    }

    @Test
    void invokesPublicInterfaceMethodOnNonPublicImplementation() throws Exception {
        Named named = new LocalNamed();

        assertEquals("local", MethodUtils.invokeExactMethod(named, "label", new Object[0]));
    }

    public interface Named {
        String label();
    }

    private static class LocalNamed implements Named {
        @Override
        public String label() {
            return "local";
        }
    }

    public static class Operations {
        public String greet(String name) {
            return "hello " + name;
        }

        public int doubleValue(Number value) {
            return value.intValue() * 2;
        }

        public int add(int left, int right) {
            return left + right;
        }
    }
}
