/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the ZIP archive subsystem by packaging and restoring an application distribution.
 */
public class ZipApplicationBundleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void restoresApplicationFilesAndDeploymentMetadata() throws IOException {
        String launcher = """
                #!/bin/sh
                exec java -jar app.jar --config config/service.properties
                """;
        String configuration = """
                service.name=inventory
                service.port=8080
                """;
        String readmeName = "docs/caf\u00e9.txt";
        Map<String, byte[]> payloads = Map.of(
                "bin/start", launcher.getBytes(StandardCharsets.UTF_8),
                "config/service.properties", configuration.getBytes(StandardCharsets.UTF_8),
                readmeName, "Deployment instructions: keep config outside the jar.\n".getBytes(StandardCharsets.UTF_8),
                "assets/logo.bin", new byte[]{0, 1, 2, 3, 127, -1, -2, 0});
        Path bundle = temporaryDirectory.resolve("inventory.zip");

        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bundle.toFile())) {
            output.setEncoding(StandardCharsets.UTF_8.name());
            ZipArchiveEntry logs = new ZipArchiveEntry("logs/");
            logs.setUnixMode(UnixStat.DIR_FLAG | 0755);
            output.putArchiveEntry(logs);
            output.closeArchiveEntry();
            for (Map.Entry<String, byte[]> payload : payloads.entrySet()) {
                boolean executable = payload.getKey().equals("bin/start");
                int method = payload.getKey().equals("assets/logo.bin") ? ZipEntry.STORED : ZipEntry.DEFLATED;
                addFile(output, payload.getKey(), payload.getValue(), executable ? 0755 : 0644, method);
            }
        }

        Path restored = Files.createDirectory(temporaryDirectory.resolve("restored"));
        try (ZipFile archive = new ZipFile(bundle.toFile())) {
            ZipArchiveEntry config = archive.getEntry("config/service.properties");
            assertNotNull(config);
            try (InputStream input = archive.getInputStream(config)) {
                assertEquals(configuration, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            ZipArchiveEntry executable = archive.getEntry("bin/start");
            assertNotNull(executable);
            assertEquals(UnixStat.FILE_FLAG | 0755, executable.getUnixMode());
            assertEquals(ZipEntry.DEFLATED, executable.getMethod());
            assertEquals(ZipEntry.STORED, archive.getEntry("assets/logo.bin").getMethod());

            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            int restoredEntries = 0;
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                assertTrue(archive.canReadEntryData(entry));
                Path destination = restored.resolve(entry.getName()).normalize();
                if (!destination.startsWith(restored)) {
                    throw new IOException("Archive entry escapes the installation directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    try (InputStream input = archive.getInputStream(entry)) {
                        Files.copy(input, destination);
                    }
                    assertEquals(entry.getSize(), Files.size(destination));
                }
                restoredEntries++;
            }
            assertEquals(payloads.size() + 1, restoredEntries);
        }

        assertTrue(Files.isDirectory(restored.resolve("logs")));
        for (Map.Entry<String, byte[]> payload : payloads.entrySet()) {
            assertArrayEquals(payload.getValue(), Files.readAllBytes(restored.resolve(payload.getKey())), payload.getKey());
        }
    }

    private static void addFile(ZipArchiveOutputStream output, String name, byte[] content, int permissions, int method) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setUnixMode(UnixStat.FILE_FLAG | permissions);
        entry.setMethod(method);
        entry.setSize(content.length);
        CRC32 checksum = new CRC32();
        checksum.update(content);
        entry.setCrc(checksum.getValue());
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }
}
