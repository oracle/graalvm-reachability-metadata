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

/// Exercises framed input's incremental byte-consumption subsystem for an application protocol reader.
public class FramedProtocolReaderTest {
    @Test
    @Timeout(60)
    public void readsAFramedProtocolRecordOneByteAtATime() throws IOException {
        byte[] record = "v1|event=inventory-reserved|order=1042|warehouse=denver\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] framedRecord = frame(record);

        ByteArrayOutputStream decodedRecord = new ByteArrayOutputStream(record.length);
        try (SnappyFramedInputStream reader = new SnappyFramedInputStream(
                new ByteArrayInputStream(framedRecord))) {
            int firstByte = reader.read();
            assertEquals('v', firstByte);
            assertTrue(reader.available() > 0);
            decodedRecord.write(firstByte);

            int nextByte;
            while ((nextByte = reader.read()) != -1) {
                decodedRecord.write(nextByte);
            }

            assertEquals(0, reader.available());
        }

        assertArrayEquals(record, decodedRecord.toByteArray());
    }

    private static byte[] frame(byte[] record) throws IOException {
        ByteArrayOutputStream framedRecord = new ByteArrayOutputStream();
        try (SnappyFramedOutputStream writer = new SnappyFramedOutputStream(framedRecord)) {
            writer.write(record);
        }
        return framedRecord.toByteArray();
    }
}
