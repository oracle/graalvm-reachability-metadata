/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.hash.HashCode;
import io.trino.jdbc.$internal.guava.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LittleEndianByteArrayInnerUnsafeByteArrayAnonymous3Test {
    @Test
    void hashesByteRangesWithFarmFingerprint() {
        byte[] bytes = "inventory-entries-with-a-longer-payload-for-fingerprinting".getBytes(StandardCharsets.UTF_8);
        HashCode expected = Hashing.farmHashFingerprint64().hashBytes(bytes);
        assertEquals(expected, Hashing.farmHashFingerprint64().hashString(new String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        bytes[10] ^= 1;
        assertNotEquals(expected, Hashing.farmHashFingerprint64().hashBytes(bytes));
    }
}
