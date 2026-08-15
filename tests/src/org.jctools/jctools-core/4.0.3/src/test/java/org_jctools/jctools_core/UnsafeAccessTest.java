/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.MpscLinkedQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeAccessTest {

    @Test
    void linkedQueueTransfersElementsInOrder() {
        MpscLinkedQueue<String> queue = new MpscLinkedQueue<>();

        assertThat(queue).isEmpty();
        assertThat(queue.offer("first message")).isTrue();
        assertThat(queue.offer("second message")).isTrue();
        assertThat(queue).hasSize(2);
        assertThat(queue.peek()).isEqualTo("first message");
        assertThat(queue.poll()).isEqualTo("first message");
        assertThat(queue.poll()).isEqualTo("second message");
        assertThat(queue.poll()).isNull();
        assertThat(queue).isEmpty();
    }
}
