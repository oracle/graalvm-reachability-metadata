/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.util.ObjectBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectBufferTest {

    @Test
    void completeAndClearBufferCreatesTypedArrayForBufferedChunks() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] firstChunk = buffer.resetAndStart();
        for (int i = 0; i < firstChunk.length; i++) {
            firstChunk[i] = "value-" + i;
        }

        Object[] lastChunk = buffer.appendCompletedChunk(firstChunk);
        lastChunk[0] = "value-12";
        lastChunk[1] = "value-13";

        String[] result = buffer.completeAndClearBuffer(lastChunk, 2, String.class);

        assertThat(result).isInstanceOf(String[].class);
        assertThat(result).containsExactly(
                "value-0", "value-1", "value-2", "value-3",
                "value-4", "value-5", "value-6", "value-7",
                "value-8", "value-9", "value-10", "value-11",
                "value-12", "value-13");
        assertThat(buffer.bufferedSize()).isZero();
    }
}
