/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpscLinkedQueue;
import org.jctools.queues.SpscUnboundedArrayQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeAccessTest {

    @Test
    void testSpscUnboundedArrayQueue() {
        SpscUnboundedArrayQueue<String> queue = new SpscUnboundedArrayQueue<>(32);
        assertThat(queue.poll()).isNull();
        queue.offer("test message");
        assertThat(queue.poll()).isEqualTo("test message");
    }

    @Test
    void mpscLinkedQueueMaintainsFifoOrderAcrossLinkedNodes() {
        MpscLinkedQueue<String> queue = new MpscLinkedQueue<>();

        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.offer("first")).isTrue();
        assertThat(queue.offer("second")).isTrue();
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.peek()).isEqualTo("first");
        assertThat(queue.poll()).isEqualTo("first");
        assertThat(queue.poll()).isEqualTo("second");
        assertThat(queue.poll()).isNull();
        assertThat(queue.isEmpty()).isTrue();
    }
}
