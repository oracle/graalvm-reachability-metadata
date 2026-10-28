/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the Zstandard compression subsystem by publishing and restoring an offline search index.
 */
public class ZstdSearchIndexSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void restoresCompressedPostingsForOfflineDocumentSearch() throws IOException {
        Map<Integer, String> documents = Map.of(
                101, "Native image deployment guide",
                102, "Native image troubleshooting",
                103, "JVM deployment guide",
                104, "Database backup guide",
                105, "Native memory monitoring",
                106, "Image asset deployment");
        Map<String, Set<Integer>> postings = new TreeMap<>();
        for (Map.Entry<Integer, String> document : documents.entrySet()) {
            for (String term : document.getValue().toLowerCase(Locale.ROOT).split(" +")) {
                postings.computeIfAbsent(term, key -> new TreeSet<>()).add(document.getKey());
            }
        }

        Path snapshot = temporaryDirectory.resolve("help-search.idx.zst");
        // The publisher stores a versioned term dictionary followed by sorted document IDs.
        // Closing the stream completes the compressed frame before the offline client opens it.
        try (ZstdCompressorOutputStream compressed = new ZstdCompressorOutputStream(
                Files.newOutputStream(snapshot), 3);
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(compressed))) {
            output.writeUTF("help-search-v1");
            output.writeInt(postings.size());
            for (Map.Entry<String, Set<Integer>> posting : postings.entrySet()) {
                output.writeUTF(posting.getKey());
                output.writeInt(posting.getValue().size());
                for (int documentId : posting.getValue()) {
                    output.writeInt(documentId);
                }
            }
        }

        Map<String, Set<Integer>> restoredPostings = new TreeMap<>();
        try (ZstdCompressorInputStream decompressed = new ZstdCompressorInputStream(
                new BufferedInputStream(Files.newInputStream(snapshot)));
                DataInputStream input = new DataInputStream(decompressed)) {
            assertEquals("help-search-v1", input.readUTF());
            int termCount = input.readInt();
            assertEquals(postings.size(), termCount);
            for (int termIndex = 0; termIndex < termCount; termIndex++) {
                String term = input.readUTF();
                int documentCount = input.readInt();
                Set<Integer> documentIds = new TreeSet<>();
                for (int documentIndex = 0; documentIndex < documentCount; documentIndex++) {
                    documentIds.add(input.readInt());
                }
                restoredPostings.put(term, documentIds);
            }
            assertEquals(-1, input.read());
        }

        assertEquals(postings, restoredPostings);
        assertEquals(List.of("Native image deployment guide", "Native image troubleshooting"),
                search(restoredPostings, "native", "image").stream().map(documents::get).toList());
        assertEquals(List.of("Native image deployment guide", "JVM deployment guide"),
                search(restoredPostings, "deployment", "guide").stream().map(documents::get).toList());
        assertEquals(Set.of(), search(restoredPostings, "backup", "native"));
        assertEquals(Set.of(), search(restoredPostings, "unpublished"));
    }

    private static Set<Integer> search(Map<String, Set<Integer>> index, String... terms) {
        Set<Integer> matches = new TreeSet<>(index.getOrDefault(terms[0], Set.of()));
        for (int termIndex = 1; termIndex < terms.length; termIndex++) {
            matches.retainAll(index.getOrDefault(terms[termIndex], Set.of()));
        }
        return matches;
    }
}
