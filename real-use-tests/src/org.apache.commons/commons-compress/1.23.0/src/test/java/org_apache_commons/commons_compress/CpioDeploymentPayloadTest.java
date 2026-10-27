/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the CPIO archive subsystem by staging a checksummed deployment payload for an appliance.
 */
public class CpioDeploymentPayloadTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void stagesBootFilesAndRetainsTheirInstallationModes() throws IOException {
        String bootScript = """
                #!/bin/sh
                exec /usr/bin/telemetry --config /etc/appliance.conf
                """;
        String configuration = """
                device.name=warehouse-reader
                upload.enabled=true
                """;
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("init", bootScript.getBytes(StandardCharsets.UTF_8));
        payloads.put("etc/appliance.conf", configuration.getBytes(StandardCharsets.UTF_8));
        payloads.put("etc/device-key.bin", new byte[]{0, -1, -128, 42, 0, 17, 127});
        payloads.put("etc/first-boot", new byte[0]);
        Map<String, Long> installationModes = Map.of(
                "init", (long) (CpioConstants.C_ISREG | 0755),
                "etc/appliance.conf", (long) (CpioConstants.C_ISREG | 0644),
                "etc/device-key.bin", (long) (CpioConstants.C_ISREG | 0600),
                "etc/first-boot", (long) (CpioConstants.C_ISREG | 0644));
        long releaseTime = 1704067200L;
        Path payload = temporaryDirectory.resolve("appliance.cpio");

        // The new ASCII CRC format stores an unsigned byte sum, not a CRC32 value.
        try (CpioArchiveOutputStream output = new CpioArchiveOutputStream(
                Files.newOutputStream(payload), CpioConstants.FORMAT_NEW_CRC)) {
            CpioArchiveEntry directory = new CpioArchiveEntry(CpioConstants.FORMAT_NEW_CRC, "etc");
            directory.setMode(CpioConstants.C_ISDIR | 0755);
            directory.setTime(releaseTime);
            output.putArchiveEntry(directory);
            output.closeArchiveEntry();
            for (Map.Entry<String, byte[]> file : payloads.entrySet()) {
                CpioArchiveEntry entry = new CpioArchiveEntry(
                        CpioConstants.FORMAT_NEW_CRC, file.getKey(), file.getValue().length);
                entry.setMode(installationModes.get(file.getKey()));
                entry.setTime(releaseTime);
                entry.setChksum(checksum(file.getValue()));
                output.putArchiveEntry(entry);
                output.write(file.getValue());
                output.closeArchiveEntry();
            }
        }

        Path staging = Files.createDirectory(temporaryDirectory.resolve("staging"));
        Map<String, Long> stagedModes = new HashMap<>();
        int memberCount = 0;
        try (CpioArchiveInputStream input = new CpioArchiveInputStream(Files.newInputStream(payload))) {
            CpioArchiveEntry entry;
            while ((entry = input.getNextCPIOEntry()) != null) {
                assertEquals(CpioConstants.FORMAT_NEW_CRC, entry.getFormat());
                assertEquals(releaseTime, entry.getTime());
                Path destination = staging.resolve(entry.getName()).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IOException("Deployment member escapes staging: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    assertEquals("etc", entry.getName());
                    assertEquals(CpioConstants.C_ISDIR | 0755, entry.getMode());
                    Files.createDirectories(destination);
                } else {
                    assertTrue(entry.isRegularFile());
                    assertTrue(payloads.containsKey(entry.getName()));
                    assertEquals(checksum(payloads.get(entry.getName())), entry.getChksum());
                    // Reading each member to EOF also asks the library to validate its checksum.
                    Files.createDirectories(destination.getParent());
                    Files.copy(input, destination);
                    assertEquals(entry.getSize(), Files.size(destination));
                    stagedModes.put(entry.getName(), entry.getMode());
                }
                memberCount++;
            }
        }

        assertEquals(5, memberCount);
        assertEquals(installationModes, stagedModes);
        assertTrue(Files.isDirectory(staging.resolve("etc")));
        for (Map.Entry<String, byte[]> file : payloads.entrySet()) {
            assertArrayEquals(file.getValue(), Files.readAllBytes(staging.resolve(file.getKey())), file.getKey());
        }
        assertEquals(configuration, Files.readString(staging.resolve("etc/appliance.conf"), StandardCharsets.UTF_8));
        assertEquals(0L, Files.size(staging.resolve("etc/first-boot")));
    }

    private static long checksum(byte[] content) {
        long sum = 0;
        for (byte value : content) {
            sum += value & 0xff;
        }
        return sum & 0xffffffffL;
    }
}
