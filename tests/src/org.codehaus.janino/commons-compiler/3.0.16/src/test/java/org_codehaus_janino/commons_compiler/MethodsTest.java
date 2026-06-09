/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.reflect.Methods;
import org.junit.jupiter.api.Test;

public class MethodsTest {
    @Test
    public void invokesMethodAndReturnsValue() throws NoSuchMethodException {
        Location location = new Location("Sample.java", 7, 3);
        Method method = Location.class.getMethod("getLineNumber");

        Integer lineNumber = Methods.invoke(method, location);

        assertThat(lineNumber).isEqualTo(7);
    }
}
