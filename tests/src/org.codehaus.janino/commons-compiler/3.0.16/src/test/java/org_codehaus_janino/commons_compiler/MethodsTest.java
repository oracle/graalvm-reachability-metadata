/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.util.reflect.Methods;
import org.junit.jupiter.api.Test;

public class MethodsTest {

    @Test
    void invokesMethodsThroughTheUtilityFacade() throws Throwable {
        Method method = InvocationTarget.class.getMethod("repeat", String.class);

        String result = Methods.invoke(method, null, "na");

        assertThat(result).isEqualTo("nana");
    }

    public static final class InvocationTarget {
        public static String repeat(String value) {
            return value + value;
        }
    }
}
