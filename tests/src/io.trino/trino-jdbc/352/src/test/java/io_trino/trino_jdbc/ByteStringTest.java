/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.okio.ByteString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ByteStringTest {
    @Test
    void preservesBinaryPayloadAndValueOperationsAcrossSerialization() throws Exception {
        ByteString original = ByteString.decodeHex("00ff102080");
        ByteString restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals("00ff102080", restored.hex());
        assertEquals(original.hashCode(), restored.hashCode());
        assertArrayEquals(new byte[] {0, (byte) 255, 16, 32, (byte) 128}, restored.toByteArray());
        assertEquals(ByteString.decodeHex("ff1020"), restored.substring(1, 4));
        byte[] exported = restored.toByteArray();
        exported[0] = 42;
        assertEquals(0, restored.getByte(0));
    }

    @Test
    void preservesUtf8AndEmptyByteStrings() throws Exception {
        ByteString restored = SerializationRoundTrip.copy(ByteString.encodeUtf8("metadata payload"));
        assertEquals("metadata payload", restored.utf8());
        assertEquals(ByteString.EMPTY, SerializationRoundTrip.copy(ByteString.EMPTY));
    }
}
