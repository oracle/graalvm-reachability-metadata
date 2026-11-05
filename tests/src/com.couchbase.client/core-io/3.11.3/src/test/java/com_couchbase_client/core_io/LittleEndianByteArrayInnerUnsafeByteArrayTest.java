/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void exercisesPublicApi() throws Exception {
        byte[] payload = "a consumer payload longer than eight bytes".getBytes(StandardCharsets.UTF_8);
        long value = Hashing.farmHashFingerprint64().hashBytes(payload).asLong();
        assertEquals(value, Hashing.farmHashFingerprint64().hashString(new String(payload, StandardCharsets.UTF_8), StandardCharsets.UTF_8).asLong());
        payload[0] = 'z';
        assertNotEquals(value, Hashing.farmHashFingerprint64().hashBytes(payload).asLong());
    }
}
