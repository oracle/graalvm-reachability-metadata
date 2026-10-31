/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ObjectBufferTest {
    @Test
    void assemblesTypedArrayAcrossBufferChunks() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String[] expected = IntStream.range(0, 80).mapToObj(i -> "entry-" + i).toArray(String[]::new);
        assertArrayEquals(expected, mapper.readValue(mapper.writeValueAsString(expected), String[].class));
    }
}
