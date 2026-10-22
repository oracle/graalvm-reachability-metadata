/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// Exercises one-shot ByteBuffer XXHash32 fingerprinting for an attachment deduplication cache.
/// Fingerprints select candidate files; byte comparison prevents hash collisions from losing content.
public class AttachmentFingerprintCacheTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void reusesIdenticalUploadsWithoutConflatingEditedAttachments() throws IOException {
        AttachmentCache cache = new AttachmentCache(directory, XXHashFactory.fastestInstance().hash32());
        byte[] original = "invoice,item,quantity,total_cents\nINV-42,storage-box,2,4800\n"
                .repeat(100).getBytes(UTF_8);
        byte[] edited = "invoice,item,quantity,total_cents\nINV-42,storage-box,3,7200\n"
                .repeat(100).getBytes(UTF_8);

        Path first = cache.store(upload("invoice.csv", original));
        Path duplicate = cache.store(upload("renamed-invoice.csv", original));
        Path revision = cache.store(upload("invoice.csv", edited));
        Path repeatedRevision = cache.store(upload("invoice-final.csv", edited));

        assertEquals(first, duplicate, "Renaming an attachment must not store its content twice");
        assertNotEquals(first, revision, "An edited attachment needs its own stored content");
        assertEquals(revision, repeatedRevision, "The revised content must also be reusable");
        assertArrayEquals(original, Files.readAllBytes(first), "The original attachment remains available");
        assertArrayEquals(edited, Files.readAllBytes(revision), "The edited attachment remains available");
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(2L, files.count(), "Four uploads should occupy only two cached files");
        }
    }

    private static ByteBuffer upload(String filename, byte[] content) {
        byte[] header = ("filename=" + filename + "\n\n").getBytes(UTF_8);
        ByteBuffer received = ByteBuffer.allocateDirect(header.length + content.length);
        received.put(header).put(content).flip();
        // The upload parser has consumed the envelope; only remaining bytes belong to the attachment.
        received.position(header.length);
        return received;
    }

    private static final class AttachmentCache {
        private final Path directory;
        private final XXHash32 fingerprints;
        private final Map<Integer, List<Path>> candidates = new HashMap<>();
        private int nextId;

        private AttachmentCache(Path directory, XXHash32 fingerprints) {
            this.directory = directory;
            this.fingerprints = fingerprints;
        }

        private Path store(ByteBuffer content) throws IOException {
            int fingerprint = fingerprints.hash(content, content.position(), content.remaining(), 0);
            byte[] bytes = new byte[content.remaining()];
            content.get(bytes);
            List<Path> bucket = candidates.computeIfAbsent(fingerprint, ignored -> new ArrayList<>());
            for (Path candidate : bucket) {
                if (Arrays.equals(bytes, Files.readAllBytes(candidate))) {
                    return candidate;
                }
            }
            Path stored = directory.resolve("attachment-" + nextId++ + ".bin");
            Files.write(stored, bytes);
            bucket.add(stored);
            return stored;
        }
    }
}
