/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.queues.SpscUnboundedArrayQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JCToolsCoreTest {

    @Test
    void testSpscUnboundedArrayQueue() {
        SpscUnboundedArrayQueue<String> queue = new SpscUnboundedArrayQueue<>(32);
        assertThat(queue.poll()).isNull();
        queue.offer("test message");
        assertThat(queue.poll()).isEqualTo("test message");
    }
}
