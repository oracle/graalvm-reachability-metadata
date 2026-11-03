/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.codehaus.plexus.util.Expand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expands application distribution archives with Plexus archive utilities.
 *
 * A deployment tool receives a versioned service bundle, expands it into a
 * staging directory, and reads the manifest and launch configuration before
 * publishing the release.
 */
class ArchiveExpansionTest {

    @Test
    void expandsServiceBundleIntoStagingDirectory(@TempDir Path workspace) throws Exception {
        Path archive = workspace.resolve("catalog-service.zip");
        writeBundle(archive);
        Path staging = workspace.resolve("staging");

        Expand expander = new Expand();
        expander.setSrc(archive.toFile());
        expander.setDest(staging.toFile());
        expander.setOverwrite(true);
        expander.execute();

        assertThat(Files.readString(staging.resolve("catalog/manifest.txt")))
                .isEqualTo("service=catalog\nrelease=2025.03\n");
        assertThat(Files.readString(staging.resolve("catalog/bin/start.sh")))
                .isEqualTo("#!/bin/sh\nexec java -jar catalog.jar\n");
    }

    private void writeBundle(Path archive) throws Exception {
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "catalog/manifest.txt", "service=catalog\nrelease=2025.03\n");
            addEntry(zip, "catalog/bin/start.sh", "#!/bin/sh\nexec java -jar catalog.jar\n");
        }
    }

    private void addEntry(ZipOutputStream zip, String name, String contents) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
