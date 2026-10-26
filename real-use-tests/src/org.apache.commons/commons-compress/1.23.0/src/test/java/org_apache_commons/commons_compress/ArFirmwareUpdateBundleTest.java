/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the AR archive subsystem by packaging and staging a device firmware update bundle.
 */
public class ArFirmwareUpdateBundleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void stagesFirmwareAfterCheckingTheBundleTargetsTheInstalledBoard() throws IOException {
        String manifest = """
                board=warehouse-controller
                release=2024.01
                firmware=controller-image.bin
                """;
        Map<String, byte[]> components = new LinkedHashMap<>();
        components.put("controller-image.bin", new byte[]{0, -1, -128, 12, 42, 0, 127});
        components.put("calibration-data.bin", "channel=temperature;offset=-12\n".repeat(400)
                .getBytes(StandardCharsets.US_ASCII));
        components.put("reset.flag", new byte[0]);
        long releaseTime = 1704067200L;
        Path bundle = temporaryDirectory.resolve("controller-update.ar");

        // BSD names preserve descriptive component names beyond AR's 16-character header field.
        try (ArArchiveOutputStream output = new ArArchiveOutputStream(Files.newOutputStream(bundle))) {
            output.setLongFileMode(ArArchiveOutputStream.LONGFILE_BSD);
            addMember(output, "release.properties", manifest.getBytes(StandardCharsets.US_ASCII), releaseTime);
            for (Map.Entry<String, byte[]> component : components.entrySet()) {
                addMember(output, component.getKey(), component.getValue(), releaseTime);
            }
        }

        Path staging = Files.createDirectory(temporaryDirectory.resolve("pending-update"));
        Set<String> stagedComponents = new HashSet<>();
        Properties release = new Properties();
        try (ArArchiveInputStream input = new ArArchiveInputStream(Files.newInputStream(bundle))) {
            ArArchiveEntry manifestEntry = input.getNextArEntry();
            assertNotNull(manifestEntry);
            assertEquals("release.properties", manifestEntry.getName());
            release.load(new StringReader(new String(input.readAllBytes(), StandardCharsets.US_ASCII)));
            assertEquals("warehouse-controller", release.getProperty("board"));
            assertEquals("2024.01", release.getProperty("release"));

            ArArchiveEntry entry;
            while ((entry = input.getNextArEntry()) != null) {
                String name = entry.getName();
                assertTrue(components.containsKey(name), "Unexpected firmware component: " + name);
                assertTrue(stagedComponents.add(name), "Duplicate firmware component: " + name);
                assertEquals(releaseTime, entry.getLastModified());
                assertEquals(0100644, entry.getMode());
                assertEquals(components.get(name).length, entry.getLength());
                Path destination = staging.resolve(name).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IOException("Firmware component escapes staging: " + name);
                }
                Files.copy(input, destination);
                assertEquals(entry.getLength(), Files.size(destination));
            }
        }

        // The odd-sized firmware member must not leak AR's alignment padding into the installed image.
        assertEquals(components.keySet(), stagedComponents);
        for (Map.Entry<String, byte[]> component : components.entrySet()) {
            assertArrayEquals(component.getValue(), Files.readAllBytes(staging.resolve(component.getKey())),
                    component.getKey());
        }
        assertArrayEquals(components.get("controller-image.bin"),
                Files.readAllBytes(staging.resolve(release.getProperty("firmware"))));
        assertEquals(0L, Files.size(staging.resolve("reset.flag")));
    }

    private static void addMember(ArArchiveOutputStream output, String name, byte[] content,
            long releaseTime) throws IOException {
        ArArchiveEntry entry = new ArArchiveEntry(name, content.length, 0, 0, 0100644, releaseTime);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }
}
