/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the framed Snappy compression subsystem by snapshotting and restoring a rendered-page cache.
 */
public class SnappyPageCacheSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void restoresCachedResponsesAcrossSnappyChunkBoundaries() throws IOException {
        Map<String, String> cachedPages = new LinkedHashMap<>();
        cachedPages.put("/", """
                <!doctype html>
                <html><body><h1>Neighborhood market</h1></body></html>
                """);
        cachedPages.put("/catalog", "<html><body><ul>\n"
                + "<li>Caf\u00e9 blend - freshly roasted coffee</li>\n".repeat(4000)
                + "</ul></body></html>\n");
        cachedPages.put("/seasonal", "");
        cachedPages.put("/contact", "<p>Contact the market at hello@example.test</p>\n");
        Path snapshot = temporaryDirectory.resolve("rendered-pages.snapshot.sz");

        // Persist a length-prefixed cache snapshot, allowing page bodies larger than writeUTF's limit.
        // The catalog alone spans several of the framed format's 64 KiB chunks.
        try (FramedSnappyCompressorOutputStream compressed = new FramedSnappyCompressorOutputStream(
                Files.newOutputStream(snapshot));
                DataOutputStream output = new DataOutputStream(compressed)) {
            output.writeUTF("rendered-page-cache-v1");
            output.writeInt(cachedPages.size());
            for (Map.Entry<String, String> page : cachedPages.entrySet()) {
                byte[] body = page.getValue().getBytes(StandardCharsets.UTF_8);
                output.writeUTF(page.getKey());
                output.writeInt(body.length);
                output.write(body);
            }
        }

        Map<String, String> restoredPages = new LinkedHashMap<>();
        try (FramedSnappyCompressorInputStream decompressed = new FramedSnappyCompressorInputStream(
                new BufferedInputStream(Files.newInputStream(snapshot)));
                DataInputStream input = new DataInputStream(decompressed)) {
            assertEquals("rendered-page-cache-v1", input.readUTF());
            int pageCount = input.readInt();
            assertEquals(4, pageCount);
            for (int page = 0; page < pageCount; page++) {
                String route = input.readUTF();
                int bodyLength = input.readInt();
                if (bodyLength < 0 || bodyLength > 1024 * 1024) {
                    throw new IOException("Invalid cached response length: " + bodyLength);
                }
                byte[] body = new byte[bodyLength];
                input.readFully(body);
                String previous = restoredPages.put(route, new String(body, StandardCharsets.UTF_8));
                if (previous != null) {
                    throw new IOException("Duplicate cached route: " + route);
                }
            }
            // Consume the end of the frame, including the last chunk's integrity check.
            assertEquals(-1, input.read());
        }

        assertEquals(cachedPages, restoredPages);
        assertEquals("", restoredPages.get("/seasonal"));
        assertEquals("<p>Contact the market at hello@example.test</p>\n", restoredPages.get("/contact"));
    }
}
