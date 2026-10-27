/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.internal.chmv8.LongAdder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Striped64Anonymous1Test {
    @Test
    public void initializesUnsafeBackedLongAdderAndAccumulatesValues() {
        LongAdder adder = new LongAdder();

        adder.increment();
        adder.add(41);
        adder.decrement();

        assertThat(adder.sum()).isEqualTo(41);
        assertThat(adder.longValue()).isEqualTo(41);
        assertThat(adder.intValue()).isEqualTo(41);
        assertThat(adder.toString()).isEqualTo("41");
    }
}
