/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the 7z archive subsystem by selectively restoring documents from an LZMA2-compressed snapshot.
 */
public class SevenZDocumentSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void restoresSelectedDocumentsWithoutExtractingTheSearchIndex() throws IOException {
        String invoice = """
                invoice,customer,total
                INV-1042,Northwind,125.50
                INV-1043,Contoso,87.25
                """;
        String notesName = "documents/caf\u00e9-notes.txt";
        Map<String, byte[]> snapshotFiles = new LinkedHashMap<>();
        snapshotFiles.put("cache/search-index.csv", "INV-1042,paid\n".repeat(1000).getBytes(StandardCharsets.UTF_8));
        snapshotFiles.put("documents/invoices.csv", invoice.getBytes(StandardCharsets.UTF_8));
        snapshotFiles.put(notesName, "Customer requested a paper receipt.\n".getBytes(StandardCharsets.UTF_8));
        snapshotFiles.put("documents/pending.csv", new byte[0]);
        FileTime snapshotTime = FileTime.fromMillis(1704067200000L);
        Path snapshot = temporaryDirectory.resolve("billing-snapshot.7z");

        try (SevenZOutputFile output = new SevenZOutputFile(snapshot.toFile())) {
            output.setContentCompression(SevenZMethod.LZMA2);
            SevenZArchiveEntry directory = new SevenZArchiveEntry();
            directory.setName("documents/");
            directory.setDirectory(true);
            output.putArchiveEntry(directory);
            output.closeArchiveEntry();
            for (Map.Entry<String, byte[]> file : snapshotFiles.entrySet()) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(file.getKey());
                entry.setLastModifiedTime(snapshotTime);
                output.putArchiveEntry(entry);
                output.write(file.getValue());
                output.closeArchiveEntry();
            }
        }

        Path restored = Files.createDirectory(temporaryDirectory.resolve("restored"));
        try (SevenZFile archive = new SevenZFile(snapshot.toFile())) {
            Map<String, SevenZArchiveEntry> catalog = new LinkedHashMap<>();
            for (SevenZArchiveEntry entry : archive.getEntries()) {
                catalog.put(entry.getName(), entry);
            }
            assertEquals(List.of("documents/", "cache/search-index.csv", "documents/invoices.csv",
                    notesName, "documents/pending.csv"), List.copyOf(catalog.keySet()));
            assertTrue(catalog.get("documents/").isDirectory());
            Files.createDirectory(restored.resolve("documents"));

            // A restore request chooses members in a different order from the snapshot's storage order.
            for (String name : List.of(notesName, "documents/invoices.csv", "documents/pending.csv")) {
                SevenZArchiveEntry entry = catalog.get(name);
                assertFalse(entry.isDirectory());
                assertEquals(snapshotFiles.get(name).length, entry.getSize());
                assertEquals(snapshotTime, entry.getLastModifiedTime());
                Path destination = restored.resolve(name).normalize();
                if (!destination.startsWith(restored)) {
                    throw new IOException("Snapshot member escapes the restore directory: " + name);
                }
                try (InputStream input = archive.getInputStream(entry)) {
                    Files.copy(input, destination);
                }
                assertArrayEquals(snapshotFiles.get(name), Files.readAllBytes(destination), name);
            }
        }

        assertEquals(invoice, Files.readString(restored.resolve("documents/invoices.csv"), StandardCharsets.UTF_8));
        assertEquals(0L, Files.size(restored.resolve("documents/pending.csv")));
        assertFalse(Files.exists(restored.resolve("cache")));
        try (var documents = Files.list(restored.resolve("documents"))) {
            assertEquals(3L, documents.count());
        }
    }
}
