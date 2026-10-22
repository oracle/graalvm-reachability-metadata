/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the TAR archive subsystem by backing up and restoring a service's configuration tree.
 */
public class TarBackupRestoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void restoresConfigurationWithLongPathsAndBackupMetadata() throws IOException {
        String tenantDirectory = "tenants/" + "regional-inventory-service-".repeat(5) + "/";
        String settings = """
                service=inventory
                region=eu-west
                audit.enabled=true
                """;
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("config/service.properties", settings.getBytes(StandardCharsets.UTF_8));
        files.put(tenantDirectory + "caf\u00e9-policy.json", """
                {"retentionDays": 30, "exportEnabled": true}
                """.getBytes(StandardCharsets.UTF_8));
        files.put("secrets/token.bin", new byte[]{0, 12, -1, 34, 0, -128});
        files.put("config/overrides.properties", new byte[0]);
        long backupTime = 1700000000000L;
        Path backup = temporaryDirectory.resolve("configuration.tar");

        try (TarArchiveOutputStream output = new TarArchiveOutputStream(Files.newOutputStream(backup), "UTF-8")) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            output.setAddPaxHeadersForNonAsciiNames(true);
            TarArchiveEntry emptyDirectory = new TarArchiveEntry("pending-imports/");
            emptyDirectory.setMode(0750);
            emptyDirectory.setModTime(backupTime);
            output.putArchiveEntry(emptyDirectory);
            output.closeArchiveEntry();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(file.getValue().length);
                entry.setMode(file.getKey().startsWith("secrets/") ? 0600 : 0640);
                entry.setModTime(backupTime);
                output.putArchiveEntry(entry);
                output.write(file.getValue());
                output.closeArchiveEntry();
            }
        }

        Path restored = Files.createDirectory(temporaryDirectory.resolve("restored"));
        Set<String> restoredNames = new HashSet<>();
        try (TarArchiveInputStream input = new TarArchiveInputStream(Files.newInputStream(backup), "UTF-8")) {
            TarArchiveEntry entry;
            while ((entry = input.getNextTarEntry()) != null) {
                assertTrue(input.canReadEntryData(entry));
                assertTrue(restoredNames.add(entry.getName()), "Duplicate backup member: " + entry.getName());
                assertEquals(backupTime, entry.getModTime().getTime());
                Path destination = restored.resolve(entry.getName()).normalize();
                if (!destination.startsWith(restored)) {
                    throw new IOException("Backup member escapes the restore directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    assertEquals("pending-imports/", entry.getName());
                    assertEquals(0750, entry.getMode());
                    Files.createDirectories(destination);
                } else {
                    assertTrue(entry.isFile());
                    assertTrue(files.containsKey(entry.getName()), "Unexpected backup member: " + entry.getName());
                    assertEquals(entry.getName().startsWith("secrets/") ? 0600 : 0640, entry.getMode());
                    Files.createDirectories(destination.getParent());
                    Files.copy(input, destination);
                    assertEquals(entry.getSize(), Files.size(destination));
                }
            }
        }

        Set<String> expectedNames = new HashSet<>(files.keySet());
        expectedNames.add("pending-imports/");
        assertEquals(expectedNames, restoredNames);
        assertTrue(Files.isDirectory(restored.resolve("pending-imports")));
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            assertArrayEquals(file.getValue(), Files.readAllBytes(restored.resolve(file.getKey())), file.getKey());
        }
    }
}
