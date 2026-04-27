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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelayQueueTest {
    @Test
    void constructorPollAndPeekRespectExpiredDelayOrdering() {
        DelayedElement oldestExpired = new DelayedElement("oldest expired", -3, TimeUnit.SECONDS);
        DelayedElement newestExpired = new DelayedElement("newest expired", -1, TimeUnit.SECONDS);
        DelayedElement unexpired = new DelayedElement("unexpired", 1, TimeUnit.DAYS);

        DelayQueue queue = new DelayQueue(Arrays.asList(unexpired, newestExpired, oldestExpired));

        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.peek()).isSameAs(oldestExpired);
        assertThat(queue.poll()).isSameAs(oldestExpired);
        assertThat(queue.poll()).isSameAs(newestExpired);
        assertThat(queue.poll()).isNull();
        assertThat(queue.peek()).isSameAs(unexpired);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void drainToTransfersOnlyExpiredElements() {
        DelayedElement firstExpired = new DelayedElement("first expired", -5, TimeUnit.MILLISECONDS);
        DelayedElement secondExpired = new DelayedElement("second expired", -1, TimeUnit.MILLISECONDS);
        DelayedElement unexpired = new DelayedElement("unexpired", 10, TimeUnit.MINUTES);
        DelayQueue queue = new DelayQueue();
        queue.add(unexpired);
        queue.add(secondExpired);
        queue.add(firstExpired);
        List<Object> drained = new ArrayList<>();

        assertThat(queue.drainTo(drained, 1)).isEqualTo(1);
        assertThat(drained).containsExactly(firstExpired);
        assertThat(queue.drainTo(drained)).isEqualTo(1);

        assertThat(drained).containsExactly(firstExpired, secondExpired);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.peek()).isSameAs(unexpired);
    }

    @Test
    void timedPollReturnsExpiredElementImmediately() throws InterruptedException {
        DelayedElement expired = new DelayedElement("expired", 0, TimeUnit.NANOSECONDS);
        DelayQueue queue = new DelayQueue();
        queue.offer(expired, 1, TimeUnit.SECONDS);

        assertThat(queue.poll(1, TimeUnit.SECONDS)).isSameAs(expired);
        assertThat(queue.poll(0, TimeUnit.NANOSECONDS)).isNull();
    }

    @Test
    void iteratorRemoveDeletesSnapshotElementByIdentity() {
        DelayedElement first = new DelayedElement("first", -2, TimeUnit.SECONDS);
        DelayedElement second = new DelayedElement("second", -1, TimeUnit.SECONDS);
        DelayQueue queue = new DelayQueue(Arrays.asList(first, second));
        Iterator iterator = queue.iterator();

        Object removed = iterator.next();
        iterator.remove();

        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.toArray()).doesNotContain(removed);
        assertThat(queue.poll()).isSameAs(removed == first ? second : first);
    }

    private static final class DelayedElement implements Delayed {
        private final String name;
        private final long delayNanos;

        private DelayedElement(String name, long delay, TimeUnit unit) {
            this.name = name;
            this.delayNanos = TimeUnit.NANOSECONDS.convert(delay, unit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Object other) {
            DelayedElement otherElement = (DelayedElement) other;
            if (delayNanos < otherElement.delayNanos) {
                return -1;
            }
            if (delayNanos > otherElement.delayNanos) {
                return 1;
            }
            return name.compareTo(otherElement.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
