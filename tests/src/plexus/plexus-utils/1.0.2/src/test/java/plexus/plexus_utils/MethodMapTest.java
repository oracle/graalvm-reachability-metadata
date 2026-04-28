/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.MethodMap;
import org.junit.jupiter.api.Test;

public class MethodMapTest {
    @Test
    void resolvesPrimitiveBooleanMethodFromBoxedArgument() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method booleanMethod = MethodMapTest.class.getMethod("accept", boolean.class);
        methodMap.add(booleanMethod);

        Method resolved = methodMap.find("accept", new Object[] { Boolean.TRUE });

        assertThat(resolved).isEqualTo(booleanMethod);
        assertThat(resolved.getParameterTypes()).containsExactly(boolean.class);
    }

    public void accept(boolean value) {
    }
}
