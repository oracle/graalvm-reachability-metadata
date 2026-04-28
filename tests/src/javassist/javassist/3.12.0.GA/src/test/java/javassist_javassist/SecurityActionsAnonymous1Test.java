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

public class SecurityActionsAnonymous1Test {
    @Test
    void privilegedActionRunReturnsDeclaredMethods() throws Exception {
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method getDeclaredMethods = securityActions.getDeclaredMethod("getDeclaredMethods", Class.class);
        getDeclaredMethods.setAccessible(true);

        Method[] methods = (Method[]) getDeclaredMethods.invoke(null, LookupTarget.class);

        assertThat(methods)
                .extracting(Method::toString)
                .anyMatch(signature -> signature.contains("LookupTarget.message()"));
    }

    public static class LookupTarget {
        public String message() {
            return "hello";
        }
    }
}
