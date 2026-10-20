/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the lock-free concurrent queue subsystem of netty-common: the shaded JCTools queues
 * that `PlatformDependent` hands out through `newMpscQueue`, `newFixedMpscQueue`,
 * `newFixedMpmcQueue` and `newSpscQueue`, together with the `newConcurrentDeque` factory.
 *
 * A telemetry agent collects samples from many instrumented threads into one shipper thread,
 * sheds load when its bounded buffer is full, fans a backlog out to several uploader threads,
 * hands encoded batches from an encoder to a writer over a single-producer queue and keeps a
 * retry deque whose failed batches go back to the front.
 */
class TelemetryPipelineConcurrentQueueTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Test
    void manyInstrumentedThreadsFeedOneShipperInPerThreadOrder() throws InterruptedException {
        Queue<Sample> inbox = PlatformDependent.newMpscQueue();
        int producers = 4;
        int samplesPerProducer = 2_500;

        List<Thread> threads = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int p = 0; p < producers; p++) {
            String source = "worker-" + p;
            Thread thread = new Thread(() -> {
                awaitQuietly(start);
                for (int seq = 0; seq < samplesPerProducer; seq++) {
                    assertThat(inbox.offer(new Sample(source, seq, seq * 0.5))).isTrue();
                }
            }, source);
            threads.add(thread);
            thread.start();
        }

        Shipper shipper = new Shipper(inbox);
        start.countDown();
        int expected = producers * samplesPerProducer;
        // The single consumer drains while producers are still writing.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (shipper.shipped() < expected && System.nanoTime() < deadline) {
            if (shipper.drainOnce() == 0) {
                Thread.onSpinWait();
            }
        }
        joinAll(threads);

        assertThat(shipper.shipped()).isEqualTo(expected);

        assertThat(inbox.poll()).isNull();
        assertThat(inbox.isEmpty()).isTrue();
        assertThat(shipper.perSource).hasSize(producers);
        for (List<Integer> sequence : shipper.perSource.values()) {
            assertThat(sequence).hasSize(samplesPerProducer);
            for (int i = 0; i < samplesPerProducer; i++) {
                assertThat(sequence.get(i)).isEqualTo(i);
            }
        }
        assertThat(shipper.sum).isEqualTo(producers * 0.5 * samplesPerProducer
                * (samplesPerProducer - 1) / 2);
    }

    @Test
    void boundedBufferShedsLoadOnceFullAndRecoversAfterDraining() {
        Queue<Sample> buffer = PlatformDependent.newFixedMpscQueue(8);
        LoadShedder shedder = new LoadShedder(buffer);

        for (int seq = 0; seq < 12; seq++) {
            shedder.record(new Sample("http", seq, 1.0));
        }

        assertThat(shedder.accepted).isEqualTo(8);
        assertThat(shedder.dropped).isEqualTo(4);
        assertThat(buffer.size()).isEqualTo(8);
        assertThat(buffer.peek().sequence).isZero();

        List<Integer> firstFlush = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            firstFlush.add(buffer.poll().sequence);
        }
        assertThat(firstFlush).containsExactly(0, 1, 2);

        for (int seq = 12; seq < 16; seq++) {
            shedder.record(new Sample("http", seq, 1.0));
        }
        assertThat(shedder.accepted).isEqualTo(11);
        assertThat(shedder.dropped).isEqualTo(5);

        List<Integer> rest = new ArrayList<>();
        for (Sample sample = buffer.poll(); sample != null; sample = buffer.poll()) {
            rest.add(sample.sequence);
        }
        assertThat(rest).containsExactly(3, 4, 5, 6, 7, 12, 13, 14);
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void chunkedQueueGrowsUpToItsMaximumCapacity() {
        Queue<Integer> spool = PlatformDependent.newMpscQueue(4096);
        int accepted = 0;
        while (spool.offer(accepted)) {
            accepted++;
            assertThat(accepted).isLessThanOrEqualTo(1 << 20);
        }

        assertThat(accepted).isEqualTo(4096);
        assertThat(spool.size()).isEqualTo(4096);
        assertThat(spool.poll()).isZero();
        assertThat(spool.offer(4096)).isTrue();
        assertThat(spool.offer(4097)).isFalse();
    }

    @Test
    void backlogFansOutToUploadersWithEveryBatchTakenExactlyOnce() throws InterruptedException {
        Queue<Integer> backlog = PlatformDependent.newFixedMpmcQueue(64);
        int batches = 2_000;
        int uploaders = 3;
        AtomicInteger uploadedTotal = new AtomicInteger();
        List<Set<Integer>> uploadedBy = new ArrayList<>();
        CountDownLatch producerDone = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        for (int u = 0; u < uploaders; u++) {
            Set<Integer> mine = new HashSet<>();
            uploadedBy.add(mine);
            Thread uploader = new Thread(() -> {
                while (true) {
                    Integer batch = backlog.poll();
                    if (batch != null) {
                        mine.add(batch);
                        uploadedTotal.incrementAndGet();
                    } else if (producerDone.getCount() == 0 && backlog.isEmpty()) {
                        return;
                    } else {
                        Thread.onSpinWait();
                    }
                }
            }, "uploader-" + u);
            threads.add(uploader);
            uploader.start();
        }

        for (int batch = 0; batch < batches; batch++) {
            while (!backlog.offer(batch)) {
                Thread.onSpinWait();
            }
        }
        producerDone.countDown();
        joinAll(threads);

        assertThat(uploadedTotal.get()).isEqualTo(batches);
        Set<Integer> union = new HashSet<>();
        int sizes = 0;
        for (Set<Integer> mine : uploadedBy) {
            sizes += mine.size();
            union.addAll(mine);
        }
        assertThat(sizes).isEqualTo(batches);
        assertThat(union).hasSize(batches);
        assertThat(backlog.poll()).isNull();
    }

    @Test
    void encoderHandsBatchesToWriterInOrderOverASingleProducerQueue() throws InterruptedException {
        Queue<String> encoded = PlatformDependent.newSpscQueue();
        int batches = 5_000;
        String poison = "EOF";

        Thread encoder = new Thread(() -> {
            for (int i = 0; i < batches; i++) {
                encoded.offer("batch-" + i + ":" + Integer.toHexString(i * 31));
            }
            encoded.offer(poison);
        }, "encoder");

        List<String> written = new ArrayList<>();
        Thread writer = new Thread(() -> {
            while (true) {
                String next = encoded.poll();
                if (next == null) {
                    Thread.onSpinWait();
                } else if (next.equals(poison)) {
                    return;
                } else {
                    written.add(next);
                }
            }
        }, "writer");

        writer.start();
        encoder.start();
        joinAll(List.of(encoder, writer));

        assertThat(written).hasSize(batches);
        assertThat(written.get(0)).isEqualTo("batch-0:0");
        assertThat(written.get(batches - 1))
                .isEqualTo("batch-" + (batches - 1) + ":" + Integer.toHexString((batches - 1) * 31));
        for (int i = 0; i < batches; i++) {
            assertThat(written.get(i)).startsWith("batch-" + i + ":");
        }
        assertThat(encoded.isEmpty()).isTrue();
    }

    @Test
    void failedBatchesGoBackToTheFrontOfTheRetryDeque() {
        Deque<String> retries = PlatformDependent.newConcurrentDeque();
        retries.offerLast("b1");
        retries.offerLast("b2");
        retries.offerLast("b3");

        String first = retries.pollFirst();
        assertThat(first).isEqualTo("b1");
        // Upload of b1 failed: it is retried before anything queued after it.
        retries.offerFirst(first);
        assertThat(retries).containsExactly("b1", "b2", "b3");

        // The oldest-looking entry at the tail is evicted when the agent is over its budget.
        assertThat(retries.pollLast()).isEqualTo("b3");
        assertThat(retries.peekFirst()).isEqualTo("b1");
        assertThat(retries.peekLast()).isEqualTo("b2");
        assertThat(retries.removeFirstOccurrence("b2")).isTrue();
        assertThat(retries).containsExactly("b1");
        assertThat(retries.pollFirst()).isEqualTo("b1");
        assertThat(retries.pollFirst()).isNull();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertThat(thread.isAlive()).as("%s finished", thread.getName()).isFalse();
        }
    }

    /// One measurement recorded by an instrumented thread.
    private static final class Sample {

        final String source;
        final int sequence;
        final double value;

        Sample(String source, int sequence, double value) {
            this.source = source;
            this.sequence = sequence;
            this.value = value;
        }
    }

    /// The single consumer that drains the agent's inbox and groups samples by source.
    private static final class Shipper {

        final Map<String, List<Integer>> perSource = new HashMap<>();
        final Queue<Sample> inbox;
        double sum;
        private int shipped;

        Shipper(Queue<Sample> inbox) {
            this.inbox = inbox;
        }

        int drainOnce() {
            int drained = 0;
            for (Sample sample = inbox.poll(); sample != null; sample = inbox.poll()) {
                perSource.computeIfAbsent(sample.source, s -> new ArrayList<>()).add(sample.sequence);
                sum += sample.value;
                drained++;
            }
            shipped += drained;
            return drained;
        }

        int shipped() {
            return shipped;
        }
    }

    /// Records into a bounded buffer and counts the samples it had to drop.
    private static final class LoadShedder {

        final Queue<Sample> buffer;
        int accepted;
        int dropped;

        LoadShedder(Queue<Sample> buffer) {
            this.buffer = buffer;
        }

        void record(Sample sample) {
            if (buffer.offer(sample)) {
                accepted++;
            } else {
                dropped++;
            }
        }
    }
}
