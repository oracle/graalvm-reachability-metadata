/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    @Test
    void resolvesDeclaredConstructor() throws Exception {
        Constructor<?> expectedConstructor = ConstructorTarget.class.getDeclaredConstructor(String.class);
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method getDeclaredConstructor = securityActions.getDeclaredMethod(
                "getDeclaredConstructor",
                Class.class,
                Class[].class);
        getDeclaredConstructor.setAccessible(true);

        Object[] arguments = new Object[] {ConstructorTarget.class, new Class<?>[] {String.class}};
        Constructor<?> constructor = (Constructor<?>) getDeclaredConstructor.invoke(null, arguments);

        assertThat(constructor).isEqualTo(expectedConstructor);
    }

    @Test
    void setsFieldValue() throws Exception {
        Field valueField = MutableTarget.class.getField("value");
        MutableTarget target = new MutableTarget("initial");
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method set = securityActions.getDeclaredMethod("set", Field.class, Object.class, Object.class);
        set.setAccessible(true);

        Object[] arguments = new Object[] {valueField, target, "updated"};
        set.invoke(null, arguments);

        assertThat(target.value()).isEqualTo("updated");
    }

    public static class ConstructorTarget {
        private final String value;

        public ConstructorTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class MutableTarget {
        public String value;

        public MutableTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
