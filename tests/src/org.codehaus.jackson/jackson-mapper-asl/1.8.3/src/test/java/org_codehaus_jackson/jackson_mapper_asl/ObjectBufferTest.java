/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.map.util.ObjectBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectBufferTest {
    @Test
    void completeAndClearBufferCreatesTypedArrayForBufferedChunks() {
        ObjectBuffer buffer = new ObjectBuffer();
        Object[] firstChunk = buffer.resetAndStart();
        List<String> expectedValues = new ArrayList<>();
        for (int i = 0; i < firstChunk.length; i++) {
            String value = "chunk-" + i;
            firstChunk[i] = value;
            expectedValues.add(value);
        }

        Object[] lastChunk = buffer.appendCompletedChunk(firstChunk);
        lastChunk[0] = "tail-0";
        lastChunk[1] = "tail-1";
        expectedValues.add("tail-0");
        expectedValues.add("tail-1");

        String[] result = buffer.completeAndClearBuffer(lastChunk, 2, String.class);

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly(expectedValues.toArray(new String[0]));
        assertThat(buffer.bufferedSize()).isZero();
        assertThat(buffer.initialCapacity()).isEqualTo(firstChunk.length);
    }
}
