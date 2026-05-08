/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MpscLinkedQueueTest {
    @Test
    void preservesSingleProducerInsertionOrderForSingleConsumer() {
        Queue<String> queue = PlatformDependent.newMpscQueue();
        queue.add("alpha");
        queue.add("bravo");
        queue.add("charlie");

        assertThat(queue.peek()).isEqualTo("alpha");
        assertThat(queue.poll()).isEqualTo("alpha");
        assertThat(queue.poll()).isEqualTo("bravo");
        assertThat(queue.poll()).isEqualTo("charlie");
        assertThat(queue.poll()).isNull();
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void acceptsElementsFromMultipleProducersForSingleConsumer() throws Exception {
        Queue<Integer> queue = PlatformDependent.newMpscQueue();
        CountDownLatch producersReady = new CountDownLatch(2);
        CountDownLatch startProducers = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.execute(new Producer(queue, producersReady, startProducers, 0));
            executor.execute(new Producer(queue, producersReady, startProducers, 100));

            assertThat(producersReady.await(5, TimeUnit.SECONDS)).isTrue();
            startProducers.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            boolean[] seen = new boolean[200];
            for (int i = 0; i < seen.length; i++) {
                Integer value = queue.poll();
                assertThat(value).isNotNull();
                seen[value] = true;
            }
            assertThat(queue.poll()).isNull();
            for (boolean valueSeen : seen) {
                assertThat(valueSeen).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class Producer implements Runnable {
        private final Queue<Integer> queue;
        private final CountDownLatch ready;
        private final CountDownLatch start;
        private final int firstValue;

        private Producer(Queue<Integer> queue, CountDownLatch ready, CountDownLatch start, int firstValue) {
            this.queue = queue;
            this.ready = ready;
            this.start = start;
            this.firstValue = firstValue;
        }

        @Override
        public void run() {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    return;
                }
                for (int i = 0; i < 100; i++) {
                    queue.add(firstValue + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
