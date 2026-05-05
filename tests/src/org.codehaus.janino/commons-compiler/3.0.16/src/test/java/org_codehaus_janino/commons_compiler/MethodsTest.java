/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.util.reflect.Methods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodsTest {
    @Test
    void invokesMethodAndReturnsValue() throws Exception {
        Method substring = String.class.getMethod("substring", int.class, int.class);

        String result = Methods.invoke(substring, "commons-compiler", 0, 7);

        assertThat(result).isEqualTo("commons");
    }
}
