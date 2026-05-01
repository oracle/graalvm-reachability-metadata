/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.util.ObjectBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectBufferTest {
    @Test
    void completeAndClearBufferReturnsTypedArrayWithBufferedValues() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] currentChunk = buffer.resetAndStart();
        TraceValue first = new TraceValue("first");
        TraceValue second = new TraceValue("second");
        currentChunk[0] = first;
        currentChunk[1] = second;

        TraceValue[] values = buffer.completeAndClearBuffer(currentChunk, 2, TraceValue.class);

        assertThat(values).containsExactly(first, second);
        assertThat(values).extracting(TraceValue::getName).containsExactly("first", "second");
        assertThat(values.getClass()).isSameAs(TraceValue[].class);
        assertThat(buffer.bufferedSize()).isZero();
    }

    public static class TraceValue {
        private final String name;

        TraceValue(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
