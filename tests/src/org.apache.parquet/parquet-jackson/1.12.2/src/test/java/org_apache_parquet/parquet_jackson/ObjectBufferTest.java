/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.ObjectBuffer;

public class ObjectBufferTest {
    @Test
    void completeAndClearBufferCreatesTypedArrayForBufferedChunks() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] firstChunk = buffer.resetAndStart();
        for (int index = 0; index < firstChunk.length; index++) {
            firstChunk[index] = "value-" + index;
        }

        Object[] secondChunk = buffer.appendCompletedChunk(firstChunk);
        secondChunk[0] = "value-12";
        secondChunk[1] = "value-13";

        String[] values = buffer.completeAndClearBuffer(secondChunk, 2, String.class);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly(
                "value-0",
                "value-1",
                "value-2",
                "value-3",
                "value-4",
                "value-5",
                "value-6",
                "value-7",
                "value-8",
                "value-9",
                "value-10",
                "value-11",
                "value-12",
                "value-13");
        assertThat(buffer.bufferedSize()).isZero();
    }
}
