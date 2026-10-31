/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.compression.snappy.SnappyCodec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnappyHelperTest {
    @Test
    void exercisesPublicApi() throws Exception {
        byte[] original = "couchbase metadata payload ".repeat(100).getBytes(StandardCharsets.UTF_8);
        SnappyCodec codec = SnappyCodec.instance();
        byte[] compressed = codec.compress(original);
        assertTrue(compressed.length < original.length);
        assertArrayEquals(original, codec.decompress(compressed));
    }
}
