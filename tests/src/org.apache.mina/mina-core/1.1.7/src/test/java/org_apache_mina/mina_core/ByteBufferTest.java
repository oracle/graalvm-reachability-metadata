/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import java.util.ArrayList;
import java.util.List;

import org.apache.mina.common.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteBufferTest {
    @Test
    void roundTripsConsecutiveLengthPrefixedObjects() throws Exception {
        List<String> message = new ArrayList<>();
        message.add("request");
        message.add("payload");
        ByteBuffer buffer = ByteBuffer.allocate(16).setAutoExpand(true);
        try {
            buffer.putObject(message);
            buffer.putObject("response");
            buffer.flip();
            int encodedLength = buffer.limit();

            assertTrue(buffer.prefixedDataAvailable(4));
            assertEquals(message, buffer.getObject());
            assertEquals(encodedLength, buffer.limit());
            assertTrue(buffer.prefixedDataAvailable(4));
            assertEquals("response", buffer.getObject());
            assertFalse(buffer.hasRemaining());
        } finally {
            buffer.release();
        }
    }
}
