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
    void defaultConstructorCreatesTypedBuffer() throws InterruptedException {
        RingBuffer<String> buffer = new RingBuffer<>(String.class);

        assertThat(buffer.capacity()).isEqualTo(16);
        assertThat(buffer.buf()).isInstanceOf(String[].class);
        assertThat(buffer.isEmpty()).isTrue();

        buffer.put("first");
        buffer.put("second");

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.take()).isEqualTo("first");
        assertThat(buffer.take()).isEqualTo("second");
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void drainsElementsInInsertionOrder() throws InterruptedException {
        RingBuffer<String> buffer = new RingBuffer<>(String.class);
        List<String> drained = new ArrayList<>();

        buffer.put("alpha");
        buffer.put("bravo");
        buffer.put("charlie");

        int drainedCount = buffer.drainTo(drained, 2);

        assertThat(drainedCount).isEqualTo(2);
        assertThat(drained).containsExactly("alpha", "bravo");
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.take()).isEqualTo("charlie");
    }
}
