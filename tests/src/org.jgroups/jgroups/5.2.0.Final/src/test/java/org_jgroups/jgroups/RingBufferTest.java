/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.RingBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RingBufferTest {
    @Test
    void createsDefaultCapacityTypedBufferAndPreservesFifoOrder() throws InterruptedException {
        RingBuffer<String> buffer = new RingBuffer<>(String.class);
        String[] backingArray = buffer.buf();

        assertThat(backingArray).hasSize(16);
        assertThat(buffer.capacity()).isEqualTo(16);
        assertThat(buffer.isEmpty()).isTrue();

        buffer.put("first").put("second");

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.take()).isEqualTo("first");
        assertThat(buffer.take()).isEqualTo("second");
        assertThat(buffer.isEmpty()).isTrue();
    }
}
