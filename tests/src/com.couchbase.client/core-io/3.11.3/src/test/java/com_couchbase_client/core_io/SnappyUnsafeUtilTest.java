/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.iq80.snappy.Snappy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnappyUnsafeUtilTest {
    @Test
    void exercisesPublicApi() throws Exception {
        byte[] original = "repeated document ".repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = new byte[Snappy.maxCompressedLength(original.length)];
        int length = Snappy.compress(original, 0, original.length, compressed, 0);
        assertTrue(length < original.length);
        byte[] result = Snappy.uncompress(Arrays.copyOf(compressed, length), 0, length);
        assertArrayEquals(original, result);
    }
}
