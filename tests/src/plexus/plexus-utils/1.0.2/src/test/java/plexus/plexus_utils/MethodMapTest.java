/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.MethodMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodMapTest {
    @Test
    void findsPrimitiveMethodUsingBoxedArgument() throws Exception {
        Method method = PrimitiveTarget.class.getMethod("setEnabled", boolean.class);
        MethodMap methodMap = new MethodMap();
        methodMap.add(method);

        Method matchedMethod = methodMap.find("setEnabled", new Object[] {Boolean.TRUE});

        assertThat(matchedMethod).isEqualTo(method);
        assertThat(matchedMethod.getParameterTypes()).containsExactly(boolean.class);
    }

    public static class PrimitiveTarget {
        private boolean enabled;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
