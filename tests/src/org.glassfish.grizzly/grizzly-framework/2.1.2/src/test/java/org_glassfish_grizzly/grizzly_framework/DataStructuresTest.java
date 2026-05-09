/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.DataStructures;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

public class DataStructuresTest {
    @Test
    void createsLinkedTransferQueueInstance() throws Exception {
        BlockingQueue<String> queue = DataStructures.getLTQInstance();

        queue.put("value");

        assertThat(queue.getClass().getName()).isEqualTo("java.util.concurrent.LinkedTransferQueue");
        assertThat(queue.take()).isEqualTo("value");
        assertThat(queue).isEmpty();
    }

    @Test
    void createsLinkedTransferQueueInstanceFromTypedFactoryMethod() {
        BlockingQueue<Integer> queue = DataStructures.getLTQInstance(Integer.class);

        assertThat(queue.offer(42)).isTrue();
        assertThat(queue.poll()).isEqualTo(42);
        assertThat(queue).isEmpty();
    }
}
