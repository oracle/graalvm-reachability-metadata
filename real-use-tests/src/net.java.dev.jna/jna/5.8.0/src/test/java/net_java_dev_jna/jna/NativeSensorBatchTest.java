/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.IntegerType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JNA's direct NIO buffer marshalling to ingest a binary sensor batch through native file I/O.
 */
public class NativeSensorBatchTest {

    @Test
    @Timeout(60)
    public void readsSuccessiveRecordsIntoADirectBufferWithoutOverwritingEarlierRecords(@TempDir Path directory)
            throws IOException {
        List<Reading> expected = List.of(
                new Reading(1_700_000_000L, 21_125),
                new Reading(1_700_000_060L, -2_750),
                new Reading(1_700_000_120L, 0));
        int recordBytes = Long.BYTES + Integer.BYTES;
        int batchBytes = Integer.BYTES + expected.size() * recordBytes;
        ByteBuffer encoded = ByteBuffer.allocate(batchBytes).order(ByteOrder.BIG_ENDIAN);
        encoded.putInt(expected.size());
        for (Reading reading : expected) {
            encoded.putLong(reading.epochSecond()).putInt(reading.milliDegrees());
        }
        Path batch = directory.resolve("sensor.batch");
        Files.write(batch, encoded.array());

        NativeInput input = Native.load(Platform.C_LIBRARY_NAME, NativeInput.class);
        int descriptor = input.open(batch.toString(), 0);
        assertTrue(descriptor >= 0, "The sensor batch must open for reading");
        try {
            ByteBuffer received = ByteBuffer.allocateDirect(batchBytes).order(ByteOrder.BIG_ENDIAN);
            // Read the header first, then append each record at the buffer's current position.
            readFully(input, descriptor, received, Integer.BYTES);
            int count = received.getInt(0);
            assertEquals(expected.size(), count, "The header must preserve the batch's record count");
            for (int index = 0; index < count; index++) {
                readFully(input, descriptor, received, recordBytes);
            }
            assertEquals(batchBytes, received.position());
            assertEquals(0L, input.read(descriptor, ByteBuffer.allocateDirect(1), new SizeT(1)).longValue(),
                    "The native stream must end exactly after the advertised records");

            received.flip();
            assertEquals(count, received.getInt(), "Later reads must not overwrite the header");
            List<Reading> actual = new ArrayList<>();
            while (received.hasRemaining()) {
                actual.add(new Reading(received.getLong(), received.getInt()));
            }
            assertEquals(expected, actual,
                    "Ingestion must preserve record order, timestamps and signed temperature values");
        } finally {
            assertEquals(0, input.close(descriptor), "The native input descriptor must be released");
        }
    }

    private static void readFully(NativeInput input, int descriptor, ByteBuffer destination, int bytes) {
        int remaining = bytes;
        while (remaining > 0) {
            long read = input.read(descriptor, destination, new SizeT(remaining)).longValue();
            assertTrue(read > 0 && read <= remaining, "A batch record must be readable in full");
            // Native read writes the direct storage but does not advance the Java buffer position.
            destination.position(destination.position() + (int) read);
            remaining -= (int) read;
        }
    }

    private record Reading(long epochSecond, int milliDegrees) {
    }

    public interface NativeInput extends Library {
        int open(String path, int flags);

        SignedSizeT read(int descriptor, ByteBuffer destination, SizeT capacity);

        int close(int descriptor);
    }

    public static class SizeT extends IntegerType {
        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }

    public static class SignedSizeT extends IntegerType {
        public SignedSizeT() {
            super(Native.SIZE_T_SIZE, 0, false);
        }
    }
}
