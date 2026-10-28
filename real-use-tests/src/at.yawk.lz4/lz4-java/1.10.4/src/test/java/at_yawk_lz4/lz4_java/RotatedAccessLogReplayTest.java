/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 block-stream continuation across independently closed, rotated access-log segments.
/// The reporting job must cross end markers, including an empty rotation, without dropping requests.
public class RotatedAccessLogReplayTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void rebuildsRouteCountsAcrossClosedAndEmptyLogRotations() throws IOException {
        List<Path> rotations = new ArrayList<>();
        List<String> expectedRequests = new ArrayList<>();
        int[] requestCounts = {240, 0, 180, 120};
        LZ4Factory codecs = LZ4Factory.fastestInstance();
        for (int rotation = 0; rotation < requestCounts.length; rotation++) {
            Path segment = directory.resolve("access-" + rotation + ".lz4block");
            rotations.add(segment);
            // Every rotation is a complete stream with its own end marker, even when idle.
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new LZ4BlockOutputStream(Files.newOutputStream(segment), 1024,
                            codecs.fastCompressor(), new CRC32(), false), UTF_8))) {
                for (int request = 0; request < requestCounts[rotation]; request++) {
                    String route = request % 3 == 0 ? "/checkout" : "/catalog";
                    String row = rotation + "-" + request + "," + route + ",200";
                    expectedRequests.add(row);
                    writer.write(row);
                    writer.write('\n');
                }
            }
        }

        Path bundle = directory.resolve("access-report-input.lz4block");
        try (OutputStream output = Files.newOutputStream(bundle)) {
            for (Path rotation : rotations) {
                Files.copy(rotation, output);
            }
        }
        assertTrue(Files.size(bundle) < String.join("\n", expectedRequests).getBytes(UTF_8).length,
                "Joining rotations should preserve their storage savings without recompressing them");

        List<String> replayed = new ArrayList<>();
        Map<String, Integer> routeCounts = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                LZ4BlockInputStream.newBuilder()
                        .withStopOnEmptyBlock(false)
                        .withDecompressor(codecs.safeDecompressor())
                        .withChecksum(new CRC32())
                        .build(Files.newInputStream(bundle)), UTF_8))) {
            String row;
            while ((row = reader.readLine()) != null) {
                replayed.add(row);
                String[] fields = row.split(",");
                assertEquals(3, fields.length, "Every replayed access record must be complete");
                assertEquals("200", fields[2]);
                routeCounts.merge(fields[1], 1, Integer::sum);
            }
        }

        assertEquals(expectedRequests, replayed,
                "Replay must retain request order and continue past the empty second rotation");
        assertEquals(Map.of("/checkout", 180, "/catalog", 360), routeCounts,
                "The report must include requests from every rotation, not only the first stream");
    }
}
