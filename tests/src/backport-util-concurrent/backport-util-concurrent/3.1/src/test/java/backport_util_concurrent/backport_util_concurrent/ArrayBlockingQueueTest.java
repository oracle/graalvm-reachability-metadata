/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBlockingQueueTest {
    @Test
    void typedArrayConversionAllocatesArrayWithComponentTypeAndQueueOrder() {
        ArrayBlockingQueue queue = new ArrayBlockingQueue(4);
        queue.add("alpha");
        queue.add("bravo");
        queue.add("charlie");

        String[] seed = new String[1];
        Object[] values = queue.toArray(seed);

        assertThat(values).isNotSameAs(seed);
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly("alpha", "bravo", "charlie");
    }
}
