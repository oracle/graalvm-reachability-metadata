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

public class JacksonObjectBufferTest {

    @Test
    void objectBufferCompletesIntoTypedArrays() {
        ObjectBuffer buffer = new ObjectBuffer();
        buffer.appendCompletedChunk(new Object[]{"a", "b" });

        String[] values = buffer.completeAndClearBuffer(new Object[]{"c" }, 1, String.class);
        assertThat(values).containsExactly("a", "b", "c");
    }
}
