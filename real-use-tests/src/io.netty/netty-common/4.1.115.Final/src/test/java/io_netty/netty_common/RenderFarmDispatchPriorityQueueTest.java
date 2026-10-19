/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.EmptyPriorityQueue;
import io.netty.util.internal.PriorityQueue;
import io.netty.util.internal.PriorityQueueNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the indexed priority-queue subsystem of netty-common: `DefaultPriorityQueue` heaps
 * whose `PriorityQueueNode` elements remember their own position per queue, giving in-place
 * `priorityChanged` reordering and constant-time `containsTyped` and `removeTyped`, together with
 * `clear`, `clearIgnoringIndexes`, array snapshots and the `EmptyPriorityQueue` placeholder.
 *
 * A render farm keeps pending jobs in one queue ordered by priority and in a second queue ordered
 * by deadline. Workers take the most urgent job, artists bump or lower a queued job, cancelled and
 * expired jobs leave both queues, a failed job is resubmitted, and a dispatcher that has been shut
 * down swaps its queue for the empty placeholder that refuses new jobs.
 */
class RenderFarmDispatchPriorityQueueTest {

    @Test
    void workersTakeTheMostUrgentJobFirstAndFifoWithinAPriority() {
        Dispatcher dispatcher = new Dispatcher();
        RenderJob trailer = dispatcher.submit("trailer", 5, 900);
        RenderJob previz = dispatcher.submit("previz", 1, 500);
        RenderJob hero = dispatcher.submit("hero-shot", 9, 700);
        RenderJob background = dispatcher.submit("background", 5, 800);
        RenderJob credits = dispatcher.submit("credits", 1, 600);

        assertThat(dispatcher.byPriority.size()).isEqualTo(5);
        assertThat(dispatcher.byPriority.peek()).isSameAs(hero);
        assertThat(dispatcher.byDeadline.peek()).isSameAs(previz);

        List<String> order = dispatcher.drainNames();

        assertThat(order)
                .containsExactly("hero-shot", "trailer", "background", "previz", "credits");
        assertThat(dispatcher.byPriority.isEmpty()).isTrue();
        assertThat(dispatcher.byDeadline.isEmpty()).isTrue();
        for (RenderJob job : List.of(trailer, previz, hero, background, credits)) {
            assertThat(job.priorityQueueIndex(dispatcher.byPriority))
                    .isEqualTo(PriorityQueueNode.INDEX_NOT_IN_QUEUE);
            assertThat(job.priorityQueueIndex(dispatcher.byDeadline))
                    .isEqualTo(PriorityQueueNode.INDEX_NOT_IN_QUEUE);
        }
        assertThat(dispatcher.takeNext()).isNull();
    }

    @Test
    void artistsBumpAndLowerQueuedJobsInPlace() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.submit("lighting", 4, 100);
        RenderJob fx = dispatcher.submit("fx-sim", 2, 200);
        RenderJob comp = dispatcher.submit("comp", 6, 300);
        dispatcher.submit("matte", 3, 400);

        dispatcher.reprioritize(fx, 8);
        assertThat(dispatcher.byPriority.peek()).isSameAs(fx);

        dispatcher.reprioritize(comp, 1);
        dispatcher.reprioritize(fx, 8);

        assertThat(dispatcher.byPriority.size()).isEqualTo(4);
        assertThat(dispatcher.drainNames()).containsExactly("fx-sim", "lighting", "matte", "comp");

        RenderJob finished = new RenderJob("finished", 7, 0, 99);
        finished.priority = 10;
        dispatcher.byPriority.priorityChanged(finished);
        assertThat(dispatcher.byPriority.isEmpty()).isTrue();
    }

    @Test
    void cancelledAndExpiredJobsLeaveBothQueues() {
        Dispatcher dispatcher = new Dispatcher();
        RenderJob storyboard = dispatcher.submit("storyboard", 3, 50);
        RenderJob layout = dispatcher.submit("layout", 7, 400);
        RenderJob texture = dispatcher.submit("texture", 5, 300);
        RenderJob rig = dispatcher.submit("rig", 2, 200);
        RenderJob animatic = dispatcher.submit("animatic", 6, 100);

        assertThat(dispatcher.cancel(texture)).isTrue();
        assertThat(dispatcher.cancel(texture)).isFalse();
        assertThat(dispatcher.byPriority.containsTyped(texture)).isFalse();
        assertThat(dispatcher.byDeadline.contains(texture)).isFalse();

        RenderJob stranger = new RenderJob("stranger", 9, 10, 100);
        assertThat(dispatcher.byPriority.removeTyped(stranger)).isFalse();
        assertThat(dispatcher.byPriority.contains(stranger)).isFalse();
        assertThat(dispatcher.byPriority.contains("layout")).isFalse();
        assertThat(dispatcher.byPriority.remove("layout")).isFalse();

        List<RenderJob> expired = dispatcher.expireDeadlinesBefore(150);

        assertThat(expired).containsExactly(storyboard, animatic);
        assertThat(dispatcher.byPriority.size()).isEqualTo(2);
        assertThat(dispatcher.byDeadline.size()).isEqualTo(2);
        assertThat(dispatcher.byPriority.containsTyped(layout)).isTrue();
        assertThat(dispatcher.byDeadline.containsTyped(rig)).isTrue();
        assertThat(dispatcher.drainNames()).containsExactly("layout", "rig");
    }

    @Test
    void aQueuedJobCannotBeSubmittedTwiceButAFailedJobIsResubmitted() {
        Dispatcher dispatcher = new Dispatcher();
        RenderJob sequence = dispatcher.submit("sequence-12", 4, 500);
        dispatcher.submit("sequence-13", 4, 600);

        assertThatThrownBy(() -> dispatcher.byPriority.offer(sequence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priorityQueueIndex");
        assertThat(dispatcher.byPriority.size()).isEqualTo(2);

        RenderJob taken = dispatcher.takeNext();
        assertThat(taken).isSameAs(sequence);
        taken.priority = 9;
        dispatcher.resubmit(taken);

        assertThat(dispatcher.byPriority.peek()).isSameAs(sequence);
        assertThat(dispatcher.drainNames()).containsExactly("sequence-12", "sequence-13");
    }

    @Test
    void snapshotsListEveryQueuedJobAndClearReleasesThem() {
        Dispatcher dispatcher = new Dispatcher();
        List<RenderJob> submitted = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            submitted.add(dispatcher.submit("shot-" + i, i % 3, 1000 - i));
        }

        RenderJob[] snapshot = dispatcher.byPriority.toArray(new RenderJob[0]);
        Object[] untyped = dispatcher.byDeadline.toArray();
        List<RenderJob> iterated = new ArrayList<>();
        for (RenderJob job : dispatcher.byPriority) {
            iterated.add(job);
        }

        assertThat(snapshot).containsExactlyInAnyOrderElementsOf(submitted);
        assertThat(untyped).containsExactlyInAnyOrderElementsOf(submitted);
        assertThat(iterated).containsExactlyInAnyOrderElementsOf(submitted);
        assertThat(snapshot[0].priority).isEqualTo(2);

        dispatcher.byPriority.clear();
        assertThat(dispatcher.byPriority.isEmpty()).isTrue();
        for (RenderJob job : submitted) {
            assertThat(job.priorityQueueIndex(dispatcher.byPriority))
                    .isEqualTo(PriorityQueueNode.INDEX_NOT_IN_QUEUE);
            assertThat(dispatcher.byDeadline.containsTyped(job)).isTrue();
        }
        assertThat(dispatcher.byPriority.offer(submitted.get(0))).isTrue();
        assertThat(dispatcher.byPriority.peek()).isSameAs(submitted.get(0));

        dispatcher.byDeadline.clearIgnoringIndexes();
        assertThat(dispatcher.byDeadline.isEmpty()).isTrue();
        assertThat(dispatcher.byDeadline.poll()).isNull();
        assertThat(dispatcher.byDeadline.containsTyped(submitted.get(3))).isFalse();
    }

    @Test
    void heapOrderSurvivesAMixedWorkloadOfSubmissionsChangesAndCancellations() {
        Dispatcher dispatcher = new Dispatcher();
        Random random = new Random(20241112L);
        List<RenderJob> queued = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            queued.add(dispatcher.submit("job-" + i, random.nextInt(20), random.nextInt(10_000)));
        }
        for (int i = 0; i < 150; i++) {
            RenderJob job = queued.get(random.nextInt(queued.size()));
            dispatcher.reprioritize(job, random.nextInt(20));
        }
        for (int i = 0; i < 100; i++) {
            RenderJob job = queued.remove(random.nextInt(queued.size()));
            assertThat(dispatcher.cancel(job)).isTrue();
        }

        List<RenderJob> expected = new ArrayList<>(queued);
        expected.sort(Dispatcher.MOST_URGENT_FIRST);
        List<RenderJob> taken = new ArrayList<>();
        RenderJob job;
        while ((job = dispatcher.takeNext()) != null) {
            taken.add(job);
        }

        assertThat(taken).hasSize(300).containsExactlyElementsOf(expected);
        assertThat(dispatcher.byDeadline.isEmpty()).isTrue();
    }

    @Test
    void aShutDownDispatcherRefusesJobsThroughTheEmptyPlaceholder() {
        PriorityQueue<RenderJob> closed = EmptyPriorityQueue.instance();
        RenderJob late = new RenderJob("late-shot", 5, 100, 1);

        assertThat(closed.offer(late)).isFalse();
        assertThat(closed.add(late)).isFalse();
        assertThat(closed.isEmpty()).isTrue();
        assertThat(closed.size()).isZero();
        assertThat(closed.poll()).isNull();
        assertThat(closed.peek()).isNull();
        assertThat(closed.containsTyped(late)).isFalse();
        assertThat(closed.removeTyped(late)).isFalse();
        assertThat(closed.iterator().hasNext()).isFalse();
        assertThat(closed.toArray()).isEmpty();
        assertThatThrownBy(closed::remove).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(closed::element).isInstanceOf(NoSuchElementException.class);
        closed.priorityChanged(late);
        closed.clearIgnoringIndexes();
        assertThat(EmptyPriorityQueue.<RenderJob>instance()).isSameAs(closed);
    }

    /// A render job that sits in several queues at once and keeps one heap index per queue.
    static final class RenderJob implements PriorityQueueNode {
        final String name;
        final long deadline;
        final long sequence;
        int priority;
        private final Map<DefaultPriorityQueue<?>, Integer> indexes = new IdentityHashMap<>();

        RenderJob(String name, int priority, long deadline, long sequence) {
            this.name = name;
            this.priority = priority;
            this.deadline = deadline;
            this.sequence = sequence;
        }

        @Override
        public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
            return indexes.getOrDefault(queue, INDEX_NOT_IN_QUEUE);
        }

        @Override
        public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
            if (i == INDEX_NOT_IN_QUEUE) {
                indexes.remove(queue);
            } else {
                indexes.put(queue, i);
            }
        }

        @Override
        public String toString() {
            return name + "(p" + priority + ", due " + deadline + ")";
        }
    }

    /// The farm's dispatcher: one heap by urgency for workers, one by deadline for expiry.
    static final class Dispatcher {
        static final Comparator<RenderJob> MOST_URGENT_FIRST = Comparator
                .comparingInt((RenderJob job) -> -job.priority)
                .thenComparingLong(job -> job.sequence);
        static final Comparator<RenderJob> EARLIEST_DEADLINE_FIRST = Comparator
                .comparingLong((RenderJob job) -> job.deadline)
                .thenComparingLong(job -> job.sequence);

        final DefaultPriorityQueue<RenderJob> byPriority =
                new DefaultPriorityQueue<>(MOST_URGENT_FIRST, 4);
        final DefaultPriorityQueue<RenderJob> byDeadline =
                new DefaultPriorityQueue<>(EARLIEST_DEADLINE_FIRST, 4);
        private long nextSequence;

        RenderJob submit(String name, int priority, long deadline) {
            RenderJob job = new RenderJob(name, priority, deadline, nextSequence++);
            resubmit(job);
            return job;
        }

        void resubmit(RenderJob job) {
            assertThat(byPriority.offer(job)).isTrue();
            assertThat(byDeadline.offer(job)).isTrue();
        }

        RenderJob takeNext() {
            RenderJob job = byPriority.poll();
            if (job != null) {
                assertThat(byDeadline.removeTyped(job)).isTrue();
            }
            return job;
        }

        void reprioritize(RenderJob job, int priority) {
            job.priority = priority;
            byPriority.priorityChanged(job);
        }

        boolean cancel(RenderJob job) {
            boolean removed = byPriority.removeTyped(job);
            return byDeadline.remove(job) && removed;
        }

        List<RenderJob> expireDeadlinesBefore(long now) {
            List<RenderJob> expired = new ArrayList<>();
            while (!byDeadline.isEmpty() && byDeadline.peek().deadline < now) {
                RenderJob job = byDeadline.poll();
                assertThat(byPriority.removeTyped(job)).isTrue();
                expired.add(job);
            }
            return expired;
        }

        List<String> drainNames() {
            List<String> names = new ArrayList<>();
            RenderJob job;
            while ((job = takeNext()) != null) {
                names.add(job.name);
            }
            return names;
        }
    }
}
