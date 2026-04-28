/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    @Test
    void privilegedExceptionActionRunReturnsDeclaredMethod() throws Exception {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method getDeclaredMethod = securityActions.getDeclaredMethod(
                "getDeclaredMethod",
                Class.class,
                String.class,
                Class[].class);
        getDeclaredMethod.setAccessible(true);

        Object[] arguments = new Object[] {LookupTarget.class, "message", new Class<?>[] {String.class}};
        Method method = (Method) getDeclaredMethod.invoke(null, arguments);

        assertThat(method.toString()).contains("LookupTarget.message(java.lang.String)");
    }

    public static class LookupTarget {
        public String message(String value) {
            return "hello " + value;
        }
    }
}
