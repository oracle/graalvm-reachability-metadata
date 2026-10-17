/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSource;
import com.google.common.io.Closer;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.LineProcessor;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hashing and I/O with `com.google.common.hash` and `com.google.common.io`: hash functions,
 * hashing streams, Bloom filter persistence, byte and char sources and sinks, `MoreFiles`, and
 * `BaseEncoding`.
 *
 * A build server stores uploaded artifacts in a content-addressed blob store. Each blob lives
 * under a path derived from its SHA-256 digest, duplicate uploads are stored once, an
 * append-only manifest records every upload, a persisted Bloom filter answers "have we ever
 * seen this digest" without touching the disk, and uploads are spread across replicas with
 * consistent hashing.
 */
public class ContentAddressedBlobStoreTest {

    @TempDir
    Path workDir;

    @Test
    void computesWellKnownDigestsAndChecksums() {
        HashCode sha256 = Hashing.sha256().hashString("hello world", UTF_8);
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", sha256.toString());
        assertEquals(256, sha256.bits());

        HashCode crc32c = Hashing.crc32c().hashString("123456789", UTF_8);
        assertEquals(0xE3069283, crc32c.asInt());

        HashCode streamed = Hashing.sha256().newHasher()
                .putString("hello", UTF_8)
                .putString(" world", UTF_8)
                .hash();
        assertEquals(sha256, streamed);
        assertEquals(sha256, HashCode.fromString(sha256.toString()));
        assertNotEquals(sha256, Hashing.sha512().hashString("hello world", UTF_8));
    }

    @Test
    void storesEachDistinctUploadOnceUnderItsDigestPath() throws IOException {
        BlobStore store = new BlobStore(workDir.resolve("store"));

        HashCode first = store.put(ByteSource.wrap("release notes v1".getBytes(UTF_8)));
        HashCode duplicate = store.put(ByteSource.wrap("release notes v1".getBytes(UTF_8)));
        HashCode second = store.put(ByteSource.wrap("release notes v2".getBytes(UTF_8)));

        assertEquals(first, duplicate);
        assertNotEquals(first, second);
        assertEquals(2, store.blobCount());

        Path blobPath = store.pathOf(first);
        String hex = first.toString();
        assertEquals(hex.substring(0, 2), blobPath.getParent().getFileName().toString());
        assertEquals(hex.substring(2) + ".blob", blobPath.getFileName().toString());
        assertEquals("blob", MoreFiles.getFileExtension(blobPath));
        assertEquals(hex.substring(2), MoreFiles.getNameWithoutExtension(blobPath));

        Optional<ByteSource> found = store.get(first);
        assertTrue(found.isPresent());
        assertEquals("release notes v1", found.get().asCharSource(UTF_8).read());
        assertTrue(store.get(Hashing.sha256().hashString("never uploaded", UTF_8)).isEmpty());
    }

    @Test
    void recordsEveryUploadInAnAppendOnlyManifest() throws IOException {
        BlobStore store = new BlobStore(workDir.resolve("store"));
        HashCode readme = store.put(ByteSource.wrap("# readme".getBytes(UTF_8)));
        store.put(ByteSource.wrap("# readme".getBytes(UTF_8)));
        HashCode license = store.put(ByteSource.wrap("CC0".getBytes(UTF_8)));

        ImmutableList<String> lines = store.manifest().readLines();
        assertEquals(3, lines.size());
        assertEquals(readme + " 8", lines.get(0));
        assertEquals(readme + " 8", lines.get(1));
        assertEquals(license + " 3", lines.get(2));

        Map<String, Integer> uploadsPerDigest = store.manifest().readLines(new LineProcessor<>() {
            private final Map<String, Integer> counts = new HashMap<>();

            @Override
            public boolean processLine(String line) {
                counts.merge(line.substring(0, line.indexOf(' ')), 1, Integer::sum);
                return true;
            }

            @Override
            public Map<String, Integer> getResult() {
                return counts;
            }
        });
        assertEquals(Map.of(readme.toString(), 2, license.toString(), 1), uploadsPerDigest);
    }

    @Test
    void detectsBlobsThatWereCorruptedOnDisk() throws IOException {
        BlobStore store = new BlobStore(workDir.resolve("store"));
        HashCode healthy = store.put(ByteSource.wrap("healthy artifact".getBytes(UTF_8)));
        HashCode damaged = store.put(ByteSource.wrap("damaged artifact".getBytes(UTF_8)));

        MoreFiles.asCharSink(store.pathOf(damaged), UTF_8, StandardOpenOption.APPEND).write("!");

        assertEquals(List.of(damaged), store.verify());
        assertTrue(store.get(healthy).isPresent());
        assertThrows(IOException.class, () -> store.get(damaged));
    }

    @Test
    void hashesStreamsWhileCopyingThem() throws IOException {
        byte[] payload = new byte[64 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        HashCode expected = ByteSource.wrap(payload).hash(Hashing.sha256());

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        try (HashingInputStream in = new HashingInputStream(Hashing.sha256(), new ByteArrayInputStream(payload));
                HashingOutputStream out = new HashingOutputStream(Hashing.murmur3_128(), received)) {
            long copied = ByteStreams.copy(in, out);
            assertEquals(payload.length, copied);
            assertEquals(expected, in.hash());
            assertEquals(Hashing.murmur3_128().hashBytes(payload), out.hash());
        }
        assertArrayEquals(payload, received.toByteArray());

        CountingOutputStream counter = new CountingOutputStream(ByteStreams.nullOutputStream());
        ByteSource.wrap(payload).slice(1024, 4096).copyTo(counter);
        assertEquals(4096, counter.getCount());
        ByteSource head = ByteSource.wrap(payload).slice(0, 100);
        ByteSource tail = ByteSource.wrap(payload).slice(100, 1L << 20);
        assertTrue(ByteSource.concat(head, tail).contentEquals(ByteSource.wrap(payload)));
    }

    @Test
    void persistsTheSeenDigestsBloomFilterAcrossRestarts() throws IOException {
        Funnel<HashCode> digestFunnel = (digest, sink) -> sink.putBytes(digest.asBytes());
        BloomFilter<HashCode> seen = BloomFilter.create(digestFunnel, 10_000, 0.001);
        List<HashCode> uploaded = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            HashCode digest = Hashing.sha256().hashString("artifact-" + i, UTF_8);
            seen.put(digest);
            uploaded.add(digest);
        }

        Path filterFile = workDir.resolve("seen.bloom");
        try (OutputStream out = Files.newOutputStream(filterFile)) {
            seen.writeTo(out);
        }
        BloomFilter<HashCode> restored;
        try (InputStream in = MoreFiles.asByteSource(filterFile).openBufferedStream()) {
            restored = BloomFilter.readFrom(in, digestFunnel);
        }

        assertEquals(seen, restored);
        for (HashCode digest : uploaded) {
            assertTrue(restored.mightContain(digest));
        }
        int falsePositives = 0;
        for (int i = 0; i < 1_000; i++) {
            if (restored.mightContain(Hashing.sha256().hashString("unseen-" + i, UTF_8))) {
                falsePositives++;
            }
        }
        assertTrue(falsePositives < 10, "false positives: " + falsePositives);
        assertEquals(500, restored.approximateElementCount(), 10);
    }

    @Test
    void spreadsUploadsAcrossReplicasWithConsistentHashing() {
        Map<Integer, Integer> perReplica = new HashMap<>();
        int moved = 0;
        for (int i = 0; i < 2_000; i++) {
            HashCode key = Hashing.murmur3_128().newHasher().putString("upload", UTF_8).putInt(i).hash();
            int withFour = Hashing.consistentHash(key, 4);
            int withFive = Hashing.consistentHash(key, 5);
            perReplica.merge(withFour, 1, Integer::sum);
            if (withFour != withFive) {
                moved++;
                assertEquals(4, withFive);
            }
        }

        assertEquals(4, perReplica.size());
        perReplica.values().forEach(count -> assertTrue(count > 400 && count < 600, "replica load " + count));
        assertTrue(moved > 300 && moved < 500, "moved " + moved);
    }

    @Test
    void encodesDigestsForUrlsAndCombinesThemIntoABuildFingerprint() {
        HashCode jar = Hashing.sha256().hashString("app.jar", UTF_8);
        HashCode config = Hashing.sha256().hashString("app.yaml", UTF_8);

        String urlToken = BaseEncoding.base64Url().omitPadding().encode(jar.asBytes());
        assertEquals(43, urlToken.length());
        assertFalse(urlToken.contains("="));
        assertArrayEquals(jar.asBytes(), BaseEncoding.base64Url().omitPadding().decode(urlToken));
        assertEquals(jar.toString(), BaseEncoding.base16().lowerCase().encode(jar.asBytes()));

        HashCode fingerprint = Hashing.combineOrdered(List.of(jar, config));
        assertNotEquals(fingerprint, Hashing.combineOrdered(List.of(config, jar)));
        assertEquals(Hashing.combineUnordered(List.of(jar, config)), Hashing.combineUnordered(List.of(config, jar)));
        assertThrows(IllegalArgumentException.class,
                () -> Hashing.combineOrdered(List.of(jar, Hashing.crc32().hashString("x", UTF_8))));
    }

    @Test
    void purgesTheWholeStoreAndReleasesEveryOpenResource() throws IOException {
        BlobStore store = new BlobStore(workDir.resolve("store"));
        HashCode a = store.put(ByteSource.wrap("a".getBytes(UTF_8)));
        HashCode b = store.put(ByteSource.wrap("b".getBytes(UTF_8)));

        Closer closer = Closer.create();
        try {
            InputStream first = closer.register(store.get(a).orElseThrow().openStream());
            InputStream second = closer.register(store.get(b).orElseThrow().openStream());
            assertEquals('a', first.read());
            assertEquals('b', second.read());
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        long files = 0;
        for (Path path : MoreFiles.fileTraverser().depthFirstPreOrder(store.root)) {
            if (Files.isRegularFile(path)) {
                files++;
            }
        }
        assertEquals(3, files);

        MoreFiles.deleteRecursively(store.root, RecursiveDeleteOption.ALLOW_INSECURE);
        assertFalse(Files.exists(store.root));
    }

    /** A content-addressed blob store laid out as `<root>/<2 hex>/<62 hex>.blob`. */
    static final class BlobStore {
        private static final HashFunction DIGEST = Hashing.sha256();

        final Path root;
        private final Path manifestFile;

        BlobStore(Path root) throws IOException {
            this.root = Files.createDirectories(root);
            this.manifestFile = root.resolve("manifest.txt");
        }

        HashCode put(ByteSource content) throws IOException {
            Hasher hasher = DIGEST.newHasher();
            byte[] bytes = content.read();
            hasher.putBytes(bytes);
            HashCode digest = hasher.hash();
            Path target = pathOf(digest);
            if (!Files.exists(target)) {
                MoreFiles.createParentDirectories(target);
                MoreFiles.asByteSink(target).write(bytes);
            }
            MoreFiles.asCharSink(manifestFile, UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                    .write(digest + " " + bytes.length + System.lineSeparator());
            return digest;
        }

        Optional<ByteSource> get(HashCode digest) throws IOException {
            Path path = pathOf(digest);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            ByteSource source = MoreFiles.asByteSource(path);
            if (!source.hash(DIGEST).equals(digest)) {
                throw new IOException("blob " + digest + " is corrupted");
            }
            return Optional.of(source);
        }

        Path pathOf(HashCode digest) {
            String hex = digest.toString();
            return root.resolve(hex.substring(0, 2)).resolve(hex.substring(2) + ".blob");
        }

        CharSource manifest() {
            return MoreFiles.asCharSource(manifestFile, UTF_8);
        }

        long blobCount() {
            long count = 0;
            for (Path path : MoreFiles.fileTraverser().breadthFirst(root)) {
                if ("blob".equals(MoreFiles.getFileExtension(path))) {
                    count++;
                }
            }
            return count;
        }

        List<HashCode> verify() throws IOException {
            List<HashCode> corrupted = new ArrayList<>();
            for (Path path : MoreFiles.fileTraverser().depthFirstPreOrder(root)) {
                if (!"blob".equals(MoreFiles.getFileExtension(path))) {
                    continue;
                }
                HashCode expected = HashCode.fromString(
                        path.getParent().getFileName() + MoreFiles.getNameWithoutExtension(path));
                if (!MoreFiles.asByteSource(path).hash(DIGEST).equals(expected)) {
                    corrupted.add(expected);
                }
            }
            return corrupted;
        }
    }
}
