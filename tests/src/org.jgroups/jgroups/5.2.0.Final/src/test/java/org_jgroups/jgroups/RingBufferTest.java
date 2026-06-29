/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.RingBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RingBufferTest {
    @Test
    void defaultConstructorCreatesTypedSixteenSlotBuffer() throws InterruptedException {
        RingBuffer<String> buffer = new RingBuffer<>(String.class);

        assertThat(buffer.capacity()).isEqualTo(16);
        assertThat(buffer.buf()).isInstanceOf(String[].class);
        assertThat(buffer.isEmpty()).isTrue();

        buffer.put("one").put("two");

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.take()).isEqualTo("one");
        assertThat(buffer.take()).isEqualTo("two");
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void drainToCollectionPreservesInsertionOrder() throws InterruptedException {
        RingBuffer<Integer> buffer = new RingBuffer<>(Integer.class);
        buffer.put(1).put(2).put(3);

        List<Integer> drained = new ArrayList<>();
        int count = buffer.drainTo(drained, 2);

        assertThat(count).isEqualTo(2);
        assertThat(drained).containsExactly(1, 2);
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.take()).isEqualTo(3);
    }
}
