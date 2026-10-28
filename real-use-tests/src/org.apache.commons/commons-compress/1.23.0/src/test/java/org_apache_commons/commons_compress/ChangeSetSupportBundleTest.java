/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.changes.ChangeSet;
import org.apache.commons.compress.changes.ChangeSetPerformer;
import org.apache.commons.compress.changes.ChangeSetResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the archive change-set subsystem by sanitizing a support bundle before sharing it with a vendor.
 */
public class ChangeSetSupportBundleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void removesPrivateFilesAndReplacesConfigurationWhilePreservingDiagnostics() throws IOException {
        String originalConfiguration = """
                service=inventory
                endpoint=https://internal.example.test
                token=example-private-token
                """;
        String publicConfiguration = """
                service=inventory
                endpoint=[redacted]
                token=[redacted]
                """;
        String disclosure = "Private logs and credentials were removed before this bundle was shared.\n";
        Map<String, byte[]> originalFiles = new LinkedHashMap<>();
        originalFiles.put("config/service.properties", originalConfiguration.getBytes(StandardCharsets.UTF_8));
        originalFiles.put("credentials.txt", "example-private-password\n".getBytes(StandardCharsets.UTF_8));
        originalFiles.put("private/access.log", "customer=example-customer\n".getBytes(StandardCharsets.UTF_8));
        originalFiles.put("private/nested/session.json", "{\"session\":\"example-session\"}\n"
                .getBytes(StandardCharsets.UTF_8));
        originalFiles.put("private-summary.txt", "Private logging is enabled.\n".getBytes(StandardCharsets.UTF_8));
        originalFiles.put("diagnostics/counters.bin", new byte[]{0, 1, -1, -128, 42, 0, 127});
        Path originalBundle = temporaryDirectory.resolve("internal-support.zip");
        Path sharedBundle = temporaryDirectory.resolve("vendor-support.zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(originalBundle.toFile())) {
            for (Map.Entry<String, byte[]> file : originalFiles.entrySet()) {
                output.putArchiveEntry(entry(file.getKey(), file.getValue().length));
                output.write(file.getValue());
                output.closeArchiveEntry();
            }
        }

        ChangeSet changes = new ChangeSet();
        changes.delete("credentials.txt");
        changes.deleteDir("private");
        ChangeSetResults audit;
        // Replacement streams must remain open until the performer has copied their contents.
        try (InputStream configuration = new ByteArrayInputStream(publicConfiguration.getBytes(StandardCharsets.UTF_8));
                InputStream readme = new ByteArrayInputStream(disclosure.getBytes(StandardCharsets.UTF_8));
                ZipFile source = new ZipFile(originalBundle.toFile());
                ZipArchiveOutputStream output = new ZipArchiveOutputStream(sharedBundle.toFile())) {
            changes.add(entry("config/service.properties", publicConfiguration.getBytes(StandardCharsets.UTF_8).length),
                    configuration, true);
            changes.add(entry("README.txt", disclosure.getBytes(StandardCharsets.UTF_8).length), readme);
            audit = new ChangeSetPerformer(changes).perform(source, output);
        }

        assertEquals(Set.of("credentials.txt", "private/access.log", "private/nested/session.json"),
                new HashSet<>(audit.getDeleted()));
        assertEquals(Set.of("config/service.properties", "README.txt"),
                new HashSet<>(audit.getAddedFromChangeSet()));
        assertEquals(Set.of("private-summary.txt", "diagnostics/counters.bin"),
                new HashSet<>(audit.getAddedFromStream()));

        Map<String, byte[]> expectedFiles = new LinkedHashMap<>();
        expectedFiles.put("config/service.properties", publicConfiguration.getBytes(StandardCharsets.UTF_8));
        expectedFiles.put("README.txt", disclosure.getBytes(StandardCharsets.UTF_8));
        expectedFiles.put("private-summary.txt", originalFiles.get("private-summary.txt"));
        expectedFiles.put("diagnostics/counters.bin", originalFiles.get("diagnostics/counters.bin"));
        Set<String> deliveredNames = new HashSet<>();
        try (ZipFile shared = new ZipFile(sharedBundle.toFile())) {
            Enumeration<ZipArchiveEntry> entries = shared.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry member = entries.nextElement();
                assertTrue(deliveredNames.add(member.getName()), "Duplicate member: " + member.getName());
                byte[] expected = expectedFiles.get(member.getName());
                assertNotNull(expected, "Unexpected member: " + member.getName());
                try (InputStream input = shared.getInputStream(member)) {
                    assertArrayEquals(expected, input.readAllBytes(), member.getName());
                }
            }
        }
        assertEquals(expectedFiles.keySet(), deliveredNames);

        // Sanitization produces a separate deliverable, leaving the internal evidence intact.
        try (ZipFile original = new ZipFile(originalBundle.toFile());
                InputStream input = original.getInputStream(original.getEntry("config/service.properties"))) {
            assertEquals(originalConfiguration, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(original.getEntry("credentials.txt"));
            assertNotNull(original.getEntry("private/nested/session.json"));
        }
    }

    private static ZipArchiveEntry entry(String name, long size) {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(size);
        entry.setTime(1704067200000L);
        return entry;
    }
}
