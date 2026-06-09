/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.util.ObjectBuffer;
import org.junit.jupiter.api.Test;

public class ObjectBufferTest {
    @Test
    public void completeAndClearBufferCreatesTypedArrayFromBufferedChunks() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] firstChunk = buffer.resetAndStart();
        for (int i = 0; i < firstChunk.length; i++) {
            firstChunk[i] = "chunk-" + i;
        }
        Object[] lastChunk = buffer.appendCompletedChunk(firstChunk);
        lastChunk[0] = "tail-0";
        lastChunk[1] = "tail-1";
        lastChunk[2] = "tail-2";

        String[] result = buffer.completeAndClearBuffer(lastChunk, 3, String.class);

        assertThat(result.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(result).containsExactly(
                "chunk-0",
                "chunk-1",
                "chunk-2",
                "chunk-3",
                "chunk-4",
                "chunk-5",
                "chunk-6",
                "chunk-7",
                "chunk-8",
                "chunk-9",
                "chunk-10",
                "chunk-11",
                "tail-0",
                "tail-1",
                "tail-2");
        assertThat(buffer.bufferedSize()).isZero();
        assertThat(buffer.initialCapacity()).isEqualTo(firstChunk.length);
    }
}
