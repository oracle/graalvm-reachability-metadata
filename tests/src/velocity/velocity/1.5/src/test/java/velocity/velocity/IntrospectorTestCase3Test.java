/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase3Test {
    @Test
    void resolvesPrimitiveOverloadForBoxedArgument() throws Exception {
        final Introspector introspector = new Introspector(new Log(new NullLogChute()));

        final Method method = introspector.getMethod(
                PrimitiveOverloads.class,
                "inspect",
                new Object[] {Integer.valueOf(3)});
        final Object result = method.invoke(new PrimitiveOverloads(), Integer.valueOf(3));

        assertThat(method.getParameterTypes()).containsExactly(int.class);
        assertThat(result).isEqualTo("int:3");
    }

    public static final class PrimitiveOverloads {
        public String inspect(final Object value) {
            return "object:" + value;
        }

        public String inspect(final int value) {
            return "int:" + value;
        }
    }
}
