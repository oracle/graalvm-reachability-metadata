/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.utils.DataStructures;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class DataStructuresTest {
    @Test
    void createsIndependentFifoQueuesThroughBothFactoryOverloads() throws InterruptedException {
        BlockingQueue<String> pending = DataStructures.getLTQInstance();
        BlockingQueue<String> completed = DataStructures.getLTQInstance(String.class);

        assertThat(pending.offer("first")).isTrue();
        assertThat(pending.offer("second")).isTrue();
        assertThat(completed).isEmpty();
        completed.put(pending.poll(10, TimeUnit.SECONDS));

        assertThat(pending.poll(10, TimeUnit.SECONDS)).isEqualTo("second");
        assertThat(pending).isEmpty();
        assertThat(completed.poll(10, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(completed).isEmpty();
    }
}
