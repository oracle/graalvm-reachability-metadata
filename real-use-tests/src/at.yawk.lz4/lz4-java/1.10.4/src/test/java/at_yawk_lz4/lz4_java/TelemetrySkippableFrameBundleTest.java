/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE.SIZE_64KB;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises LZ4 skippable metadata frames in a concatenated telemetry segment bundle.
/// A collector joins independently compressed segments without recompressing their measurements.
public class TelemetrySkippableFrameBundleTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void importsAllMeasurementsWithoutTreatingOptionalMetadataAsTelemetry() throws IOException {
        Path bundle = directory.resolve("telemetry-bundle.lz4");
        List<String> expected = new ArrayList<>();
        try (OutputStream output = Files.newOutputStream(bundle)) {
            for (int segment = 0; segment < 3; segment++) {
                writeMetadata(output, "source=gateway-" + segment + "\nschema=energy-v1\n");
                StringBuilder measurements = new StringBuilder();
                for (int sample = 0; sample < 250; sample++) {
                    String row = "gateway-" + segment + "," + sample + "," + (100 + segment);
                    expected.add(row);
                    measurements.append(row).append('\n');
                }
                output.write(compressSegment(measurements.toString()));
            }
            writeMetadata(output, "collector=regional-energy\nsegments=3\n");
        }

        List<String> imported = new ArrayList<>();
        long totalWattHours = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new LZ4FrameInputStream(Files.newInputStream(bundle)), UTF_8))) {
            String row;
            while ((row = reader.readLine()) != null) {
                imported.add(row);
                String[] fields = row.split(",");
                assertEquals(3, fields.length, "Optional metadata must not enter the measurement parser");
                totalWattHours += Long.parseLong(fields[2]);
            }
        }

        assertEquals(expected, imported, "Every segment must retain its measurements and ordering");
        assertEquals(75750L, totalWattHours, "The imported energy total must include all three gateways");
    }

    private static byte[] compressSegment(String measurements) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream output = new LZ4FrameOutputStream(bytes, SIZE_64KB,
                BLOCK_INDEPENDENCE, CONTENT_CHECKSUM)) {
            output.write(measurements.getBytes(UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void writeMetadata(OutputStream output, String metadata) throws IOException {
        byte[] payload = metadata.getBytes(UTF_8);
        // The LZ4 frame format reserves this magic for opaque, length-delimited skippable data.
        byte[] header = ByteBuffer.allocate(2 * Integer.BYTES).order(LITTLE_ENDIAN)
                .putInt(0x184D2A50).putInt(payload.length).array();
        output.write(header);
        output.write(payload);
    }
}
