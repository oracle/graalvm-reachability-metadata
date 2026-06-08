/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificOverloadAndRejectsAmbiguousMatch() throws Exception {
        final Introspector introspector = new Introspector(new Log(new NullLogChute()));

        final Method stringMethod = introspector.getMethod(
                OverloadedMethods.class,
                "choose",
                new Object[] {"value"});
        assertThat(stringMethod).isNotNull();
        assertThat(stringMethod.getParameterTypes()).containsExactly(String.class);
        assertThat(stringMethod.invoke(new OverloadedMethods(), "value")).isEqualTo("string:value");

        final Method listOrRunnable = introspector.getMethod(
                OverloadedMethods.class,
                "ambiguous",
                new Object[] {new RunnableArrayList()});
        assertThat(listOrRunnable).isNull();
    }

    public static class OverloadedMethods {
        public String choose(final Object value) {
            return "object:" + value;
        }

        public String choose(final String value) {
            return "string:" + value;
        }

        public String ambiguous(final List<?> values) {
            return "list:" + values.size();
        }

        public String ambiguous(final Runnable runnable) {
            return "runnable";
        }
    }

    public static final class RunnableArrayList extends ArrayList<Object> implements Runnable {
        @Override
        public void run() {
        }
    }
}
