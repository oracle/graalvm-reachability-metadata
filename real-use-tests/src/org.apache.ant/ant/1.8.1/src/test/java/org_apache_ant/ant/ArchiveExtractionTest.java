/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Ant's archive extraction subsystem to install an application release.
public class ArchiveExtractionTest {
    @Test
    @Timeout(60)
    public void extractsReleaseFilesIntoApplicationDirectory() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-archive-extraction-");
        try {
            Path archive = workDirectory.resolve("dist/inventory.zip");
            Files.createDirectories(archive.getParent());
            writeArchive(archive);
            Path installation = workDirectory.resolve("installed");

            Project project = new Project();
            project.init();
            Expand expand = new Expand();
            expand.setProject(project);
            expand.setSrc(archive.toFile());
            expand.setDest(installation.toFile());
            expand.setFailOnEmptyArchive(true);
            expand.execute();

            Path configuration = installation.resolve("config/application.properties");
            Path launcher = installation.resolve("bin/start.sh");
            assertTrue(Files.isRegularFile(configuration));
            assertTrue(Files.isRegularFile(launcher));
            assertEquals("service.name=inventory\nrelease=2026.04\n",
                    Files.readString(configuration, StandardCharsets.UTF_8));
            assertEquals("#!/bin/sh\nexec inventory\n",
                    Files.readString(launcher, StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void writeArchive(Path archive) throws IOException {
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "config/application.properties", "service.name=inventory\nrelease=2026.04\n");
            addEntry(zip, "bin/start.sh", "#!/bin/sh\nexec inventory\n");
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ArchiveExtractionTest::delete);
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up " + path, exception);
        }
    }
}
