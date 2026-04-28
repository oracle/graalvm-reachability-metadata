/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous4Test {
    @Test
    void privilegedExceptionActionRunReturnsDeclaredConstructor() throws Exception {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method getDeclaredConstructor = securityActions.getDeclaredMethod(
                "getDeclaredConstructor",
                Class.class,
                Class[].class);
        getDeclaredConstructor.setAccessible(true);

        Object[] arguments = new Object[] {ConstructorTarget.class, new Class<?>[] {String.class, int.class}};
        Constructor<?> declaredConstructor = (Constructor<?>) getDeclaredConstructor.invoke(null, arguments);

        assertThat(declaredConstructor.toString()).contains("ConstructorTarget(java.lang.String,int)");
    }

    public static class ConstructorTarget {
        private final String label;
        private final int count;

        public ConstructorTarget(String label, int count) {
            this.label = label;
            this.count = count;
        }

        public String label() {
            return label;
        }

        public int count() {
            return count;
        }
    }
}
