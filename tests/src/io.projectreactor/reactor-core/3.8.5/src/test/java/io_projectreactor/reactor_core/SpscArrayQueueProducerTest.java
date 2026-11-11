/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.Queue;

import org.junit.jupiter.api.Test;
import reactor.util.concurrent.Queues;

import static org.assertj.core.api.Assertions.assertThat;

public class SpscArrayQueueProducerTest {

    @Test
    void rejectsOverflowAndReusesCapacityAfterPolling() {
        Queue<Integer> queue = Queues.<Integer>get(8).get();
        for (int value = 0; value < 8; value++) {
            assertThat(queue.offer(value)).isTrue();
        }
        assertThat(queue.offer(8)).isFalse();
        assertThat(queue.size()).isEqualTo(8);

        assertThat(queue.poll()).isEqualTo(0);
        assertThat(queue.offer(8)).isTrue();

        for (int value = 1; value <= 8; value++) {
            assertThat(queue.poll()).isEqualTo(value);
        }
        assertThat(queue.isEmpty()).isTrue();
    }
}
