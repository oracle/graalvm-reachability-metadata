/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

import org.iq80.snappy.SnappyFramedInputStream;
import org.iq80.snappy.SnappyFramedOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the framed stream incremental-writer subsystem for an application publishing a binary audit event.
public class IncrementalAuditEventTest {
    @Test
    @Timeout(60)
    public void publishesAnAuditEventOneByteAtATime() throws IOException {
        byte[] event = "v1|audit|actor=warehouse-service|action=stock-adjusted|sku=HW-1042|delta=7\n"
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream storedEvent = new ByteArrayOutputStream();

        try (SnappyFramedOutputStream writer = new SnappyFramedOutputStream(storedEvent)) {
            for (byte value : event) {
                writer.write(value & 0xff);
            }
        }

        byte[] restoredEvent = new byte[event.length + 6];
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(storedEvent.toByteArray()))) {
            int bytesRead = reader.read(restoredEvent, 3, event.length);
            assertEquals(event.length, bytesRead);
            assertTrue(reader.available() >= 0);
        }

        byte[] eventSlice = new byte[event.length];
        System.arraycopy(restoredEvent, 3, eventSlice, 0, event.length);
        assertArrayEquals(event, eventSlice);
    }
}
