/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4CompressorWithLength;
import net.jpountz.lz4.LZ4DecompressorWithLength;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 length-prefixed codec adapters for a disk-backed application session cache.
/// Each cached document carries its original length, so a restarted worker needs no size index.
public class LengthPrefixedSessionCacheTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void restoresVariableSizedSessionsAfterRestartAndReplacesAnExistingSession() throws IOException {
        Map<String, String> sessions = new LinkedHashMap<>();
        sessions.put("guest", sessionDocument("guest", 0));
        sessions.put("retail", sessionDocument("customer-42", 3));
        sessions.put("wholesale", sessionDocument("customer-900", 1200));
        SessionCache writer = new SessionCache(directory);
        for (Map.Entry<String, String> session : sessions.entrySet()) {
            writer.put(session.getKey(), session.getValue());
        }

        assertTrue(Files.size(directory.resolve("wholesale.lz4"))
                        < sessions.get("wholesale").getBytes(UTF_8).length,
                "A large repetitive session should consume less cache storage");

        // A fresh worker knows only the keys and cache directory, not the original document sizes.
        SessionCache restarted = new SessionCache(directory);
        for (String key : List.of("wholesale", "guest", "retail")) {
            assertEquals(sessions.get(key), restarted.get(key),
                    "Restoring a session must retain its customer and every cart item");
        }

        String updated = sessionDocument("customer-42", 1);
        restarted.put("retail", updated);
        SessionCache nextWorker = new SessionCache(directory);
        assertEquals(updated, nextWorker.get("retail"),
                "Replacing a session must not retain bytes from its previous, longer value");
        assertEquals(sessions.get("guest"), nextWorker.get("guest"));
        assertEquals(sessions.get("wholesale"), nextWorker.get("wholesale"),
                "Updating one session must leave other customers' sessions intact");
    }

    private static String sessionDocument(String customer, int cartItems) {
        StringBuilder document = new StringBuilder("{\"customer\":\"");
        document.append(customer).append("\",\"currency\":\"USD\",\"cart\":[");
        for (int item = 0; item < cartItems; item++) {
            if (item > 0) {
                document.append(',');
            }
            document.append("{\"sku\":\"warehouse-storage-box-").append(item)
                    .append("\",\"quantity\":2,\"unitPriceCents\":2400}");
        }
        return document.append("]}").toString();
    }

    private static final class SessionCache {
        private final Path directory;
        private final LZ4CompressorWithLength encoder;
        private final LZ4DecompressorWithLength decoder;

        private SessionCache(Path directory) {
            this.directory = directory;
            LZ4Factory factory = LZ4Factory.fastestInstance();
            encoder = new LZ4CompressorWithLength(factory.fastCompressor());
            decoder = new LZ4DecompressorWithLength(factory.safeDecompressor());
        }

        private void put(String key, String document) throws IOException {
            byte[] packed = encoder.compress(document.getBytes(UTF_8));
            Files.write(directory.resolve(key + ".lz4"), packed);
        }

        private String get(String key) throws IOException {
            byte[] packed = Files.readAllBytes(directory.resolve(key + ".lz4"));
            return new String(decoder.decompress(packed), UTF_8);
        }
    }
}
