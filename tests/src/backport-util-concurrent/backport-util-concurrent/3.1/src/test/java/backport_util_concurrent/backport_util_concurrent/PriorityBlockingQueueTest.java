/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.PriorityBlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityBlockingQueueTest {
    @Test
    void constructorAndQueueOperationsUsePriorityOrdering() throws Exception {
        PriorityBlockingQueue queue = new PriorityBlockingQueue();

        assertThat(queue.offer(Integer.valueOf(30))).isTrue();
        queue.put(Integer.valueOf(10));
        assertThat(queue.offer(Integer.valueOf(20), 1L, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(queue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.peek()).isEqualTo(Integer.valueOf(10));
        assertThat(queue.take()).isEqualTo(Integer.valueOf(10));
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(20));
        assertThat(queue.poll(1L, TimeUnit.MILLISECONDS)).isEqualTo(Integer.valueOf(30));
        assertThat(queue.poll()).isNull();
    }

    @Test
    void comparatorConstructorAndDrainToRetainCustomPriorityOrder() {
        Comparator reverseOrder = new Comparator() {
            @Override
            public int compare(Object left, Object right) {
                return ((Integer) right).compareTo((Integer) left);
            }
        };
        PriorityBlockingQueue queue = new PriorityBlockingQueue(4, reverseOrder);
        queue.add(Integer.valueOf(1));
        queue.add(Integer.valueOf(3));
        queue.add(Integer.valueOf(2));

        assertThat(queue.comparator()).isSameAs(reverseOrder);
        List drained = new ArrayList();
        int drainedCount = queue.drainTo(drained, 2);

        assertThat(drainedCount).isEqualTo(2);
        assertThat(drained).containsExactly(Integer.valueOf(3), Integer.valueOf(2));
        assertThat(queue.contains(Integer.valueOf(1))).isTrue();
        assertThat(queue.remove(Integer.valueOf(1))).isTrue();
        assertThat(queue.size()).isZero();
    }

    @Test
    void typedArrayConversionPreservesRuntimeComponentType() {
        PriorityBlockingQueue queue = new PriorityBlockingQueue();
        queue.add("bravo");
        queue.add("alpha");

        String[] values = (String[]) queue.toArray(new String[0]);

        assertThat(values).containsExactly("alpha", "bravo");
    }
}
