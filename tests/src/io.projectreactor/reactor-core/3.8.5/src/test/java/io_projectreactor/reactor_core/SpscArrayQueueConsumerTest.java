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

public class SpscArrayQueueConsumerTest {

    @Test
    void preservesFifoOrderAcrossRepeatedArrayWraparound() {
        Queue<String> queue = Queues.<String>get(8).get();
        for (int batch = 0; batch < 4; batch++) {
            for (int index = 0; index < 8; index++) {
                assertThat(queue.offer(batch + ":" + index)).isTrue();
            }
            for (int index = 0; index < 8; index++) {
                assertThat(queue.peek()).isEqualTo(batch + ":" + index);
                assertThat(queue.poll()).isEqualTo(batch + ":" + index);
            }
            assertThat(queue.poll()).isNull();
            assertThat(queue.size()).isZero();
        }
    }
}
