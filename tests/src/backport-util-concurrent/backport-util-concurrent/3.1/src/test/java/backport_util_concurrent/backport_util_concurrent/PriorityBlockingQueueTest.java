/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.PriorityBlockingQueue;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PriorityBlockingQueueTest {
    @Test
    void naturalOrderingAppliesAcrossQueueOperations() throws InterruptedException {
        PriorityBlockingQueue queue = new PriorityBlockingQueue(2);

        assertThat(queue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(queue.offer(Integer.valueOf(7))).isTrue();
        queue.put(Integer.valueOf(1));
        assertThat(queue.offer(Integer.valueOf(9), 1, TimeUnit.SECONDS)).isTrue();
        assertThat(queue.add(Integer.valueOf(3))).isTrue();

        assertThat(queue.size()).isEqualTo(4);
        assertThat(queue.contains(Integer.valueOf(7))).isTrue();
        assertThat(queue.peek()).isEqualTo(Integer.valueOf(1));
        assertThat(queue.take()).isEqualTo(Integer.valueOf(1));
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(3));
        assertThat(queue.poll(0, TimeUnit.NANOSECONDS)).isEqualTo(Integer.valueOf(7));
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(9));
        assertThat(queue.poll()).isNull();
        assertThat(queue.size()).isZero();
    }

    @Test
    void drainToRemovesElementsInPriorityOrder() {
        PriorityBlockingQueue queue = new PriorityBlockingQueue(Arrays.asList(Integer.valueOf(4), Integer.valueOf(1),
                Integer.valueOf(7), Integer.valueOf(3)));
        List<Object> limitedDrain = new ArrayList<>();
        List<Object> remainingDrain = new ArrayList<>();

        assertThat(queue.drainTo(limitedDrain, 2)).isEqualTo(2);
        assertThat(limitedDrain).containsExactly(Integer.valueOf(1), Integer.valueOf(3));
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.drainTo(remainingDrain)).isEqualTo(2);
        assertThat(remainingDrain).containsExactly(Integer.valueOf(4), Integer.valueOf(7));
        assertThat(queue.size()).isZero();
    }

    @Test
    void arraySnapshotsAreDetachedFromQueueStorage() {
        PriorityBlockingQueue queue = new PriorityBlockingQueue(Arrays.asList(Integer.valueOf(5), Integer.valueOf(2),
                Integer.valueOf(8)));

        Object[] snapshot = queue.toArray();
        Integer[] target = new Integer[4];
        Object[] typedSnapshot = queue.toArray(target);
        queue.clear();

        assertThat(snapshot).containsExactlyInAnyOrder(Integer.valueOf(2), Integer.valueOf(5), Integer.valueOf(8));
        assertThat(typedSnapshot).isSameAs(target);
        assertThat(target).contains(Integer.valueOf(2), Integer.valueOf(5), Integer.valueOf(8));
        assertThat(target[3]).isNull();
        assertThat(queue.size()).isZero();
    }

    @Test
    void comparatorConstructorUsesSuppliedOrdering() {
        ReverseIntegerComparator comparator = new ReverseIntegerComparator();
        PriorityBlockingQueue queue = new PriorityBlockingQueue(2, comparator);

        queue.add(Integer.valueOf(1));
        queue.add(Integer.valueOf(9));
        queue.add(Integer.valueOf(7));

        assertThat(queue.comparator()).isSameAs(comparator);
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(9));
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(7));
        assertThat(queue.poll()).isEqualTo(Integer.valueOf(1));
    }

    @Test
    void iteratorRemoveDeletesSnapshotElementByIdentity() {
        PriorityBlockingQueue queue = new PriorityBlockingQueue();
        Task first = new Task("first", 1);
        Task second = new Task("second", 2);
        Task third = new Task("third", 3);
        queue.add(first);
        queue.add(second);
        queue.add(third);
        Iterator iterator = queue.iterator();

        Object removed = iterator.next();
        iterator.remove();

        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.contains(removed)).isFalse();
        assertThat(queue.toArray()).doesNotContain(removed);
    }

    private static final class ReverseIntegerComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer left, Integer right) {
            return right.compareTo(left);
        }
    }

    private static final class Task implements Comparable<Task> {
        private final String name;
        private final int priority;

        private Task(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public int compareTo(Task other) {
            return Integer.compare(priority, other.priority);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
