/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.atomic.MpscGrowableAtomicArrayQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseMpscLinkedAtomicArrayQueueTest {

    @Test
    void growsAcrossBuffersAndReusesCapacityAfterDraining() {
        MpscGrowableAtomicArrayQueue<Integer> queue = new MpscGrowableAtomicArrayQueue<>(2, 16);

        assertThat(queue.isEmpty()).isTrue();
        for (int i = 0; i < 16; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
        assertThat(queue.size()).isEqualTo(16);
        assertThat(queue.currentProducerIndex()).isEqualTo(16);
        assertThat(queue.offer(16)).isFalse();

        for (int i = 0; i < 16; i++) {
            assertThat(queue.peek()).isEqualTo(i);
            assertThat(queue.poll()).isEqualTo(i);
        }
        assertThat(queue.currentConsumerIndex()).isEqualTo(16);
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.poll()).isNull();

        for (int i = 16; i < 32; i++) {
            assertThat(queue.offer(i)).isTrue();
        }
        assertThat(queue.size()).isEqualTo(16);
        assertThat(queue.offer(32)).isFalse();
        for (int i = 16; i < 32; i++) {
            assertThat(queue.poll()).isEqualTo(i);
        }
        assertThat(queue.currentProducerIndex()).isEqualTo(32);
        assertThat(queue.currentConsumerIndex()).isEqualTo(32);
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.peek()).isNull();
    }
}
