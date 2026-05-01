/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.DelayQueue;
import edu.emory.mathcs.backport.java.util.concurrent.Delayed;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DelayQueueTest {
    private static final long LONG_DELAY_NANOS = 60_000_000_000L;
    private static final long EXPIRED_DELAY_NANOS = -1_000_000L;

    @Test
    void pollReturnsOnlyExpiredElementsInDelayOrder() {
        long baseNanos = System.nanoTime();
        DelayQueue queue = new DelayQueue();
        DelayedElement delayed = new DelayedElement("delayed", baseNanos + LONG_DELAY_NANOS);
        DelayedElement expired = new DelayedElement("expired", baseNanos + EXPIRED_DELAY_NANOS);

        assertThat(queue.offer(delayed)).isTrue();
        assertThat(queue.offer(expired)).isTrue();

        assertThat(queue.peek()).isSameAs(expired);
        assertThat(queue.poll()).isSameAs(expired);
        assertThat(queue.poll()).isNull();
        assertThat(queue.peek()).isSameAs(delayed);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void drainToAndIteratorRemovalHandleExpiredAndUnexpiredElements() {
        long baseNanos = System.nanoTime();
        DelayQueue queue = new DelayQueue();
        DelayedElement firstExpired = new DelayedElement("firstExpired", baseNanos + EXPIRED_DELAY_NANOS);
        DelayedElement secondExpired = new DelayedElement("secondExpired", baseNanos + EXPIRED_DELAY_NANOS + 1);
        DelayedElement delayed = new DelayedElement("delayed", baseNanos + LONG_DELAY_NANOS);
        List drained = new ArrayList();

        queue.add(delayed);
        queue.add(secondExpired);
        queue.add(firstExpired);

        assertThat(queue.drainTo(drained, 1)).isEqualTo(1);
        assertThat(drained).containsExactly(firstExpired);
        assertThat(queue.drainTo(drained)).isEqualTo(1);
        assertThat(drained).containsExactly(firstExpired, secondExpired);
        assertThat(queue.size()).isEqualTo(1);

        Iterator iterator = queue.iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isSameAs(delayed);
        iterator.remove();

        assertThat(queue.size()).isZero();
        assertThat(queue.toArray()).isEmpty();
    }

    private static final class DelayedElement implements Delayed {
        private final String label;
        private final long deadlineNanos;

        private DelayedElement(String label, long deadlineNanos) {
            this.label = label;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Object other) {
            DelayedElement otherElement = (DelayedElement) other;
            return Long.compare(deadlineNanos, otherElement.deadlineNanos);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
