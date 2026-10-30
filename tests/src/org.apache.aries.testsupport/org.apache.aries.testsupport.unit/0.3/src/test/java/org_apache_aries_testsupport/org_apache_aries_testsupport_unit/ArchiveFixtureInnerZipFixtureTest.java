/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.aries.unittest.fixture.ArchiveFixture;
import org.apache.aries.unittest.fixture.ArchiveFixture.ZipFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArchiveFixtureInnerZipFixtureTest {
    @Test
    void packagesClasspathBinaryContentAlongsideTextEntries() throws Exception {
        ZipFixture archive = ArchiveFixture.newZip()
                .binary("data/payload.txt", "aries-fixtures/payload.txt")
                .file("description.txt", "fixture archive");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        archive.writeOut(output);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                input.closeEntry();
            }
        }
        assertEquals(Map.of("data/payload.txt", "Aries fixture payload\n",
                "description.txt", "fixture archive\n"), entries);
    }
}
