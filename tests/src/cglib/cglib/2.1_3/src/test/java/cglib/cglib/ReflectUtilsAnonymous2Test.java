/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.ReflectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilsAnonymous2Test {
    @Test
    void initializesReflectionSupportAndInvokesDefaultConstructor() {
        Counter first = (Counter) ReflectUtils.newInstance(Counter.class);
        Counter second = (Counter) ReflectUtils.newInstance(Counter.class);

        first.increment();

        assertThat(first.value()).isEqualTo(1);
        assertThat(second.value()).isZero();
        assertThat(first).isNotSameAs(second);
    }

    public static class Counter {
        private int value;

        public Counter() {
            value = 0;
        }

        public void increment() {
            value++;
        }

        public int value() {
            return value;
        }
    }
}
