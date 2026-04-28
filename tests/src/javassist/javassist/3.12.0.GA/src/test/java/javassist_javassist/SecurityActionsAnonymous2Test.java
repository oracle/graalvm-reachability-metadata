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

public class SecurityActionsAnonymous2Test {
    @Test
    void privilegedActionRunReturnsDeclaredConstructors() throws Exception {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method getDeclaredConstructors = securityActions.getDeclaredMethod("getDeclaredConstructors", Class.class);
        getDeclaredConstructors.setAccessible(true);

        Constructor<?>[] constructors = (Constructor<?>[]) getDeclaredConstructors.invoke(null, ConstructorTarget.class);

        assertThat(constructors)
                .extracting(Constructor::toString)
                .anyMatch(signature -> signature.contains("ConstructorTarget(java.lang.String)"));
    }

    public static class ConstructorTarget {
        private final String value;

        public ConstructorTarget() {
            this("default");
        }

        public ConstructorTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
