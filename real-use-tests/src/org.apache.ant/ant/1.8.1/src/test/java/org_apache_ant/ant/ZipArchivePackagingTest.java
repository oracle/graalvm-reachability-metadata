/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Ant's archive packaging subsystem to create and inspect a distributable ZIP file.
public class ZipArchivePackagingTest {
    @Test
    @Timeout(60)
    public void packagesApplicationFilesIntoZipArchive() throws Exception {
        Path workDirectory = Files.createTempDirectory("ant-zip-package-");
        try {
            Path inputDirectory = Files.createDirectories(workDirectory.resolve("input"));
            Files.createDirectories(inputDirectory.resolve("docs"));
            Files.createDirectories(inputDirectory.resolve("config"));
            Files.writeString(inputDirectory.resolve("docs/readme.txt"),
                    "Inventory service\n", StandardCharsets.UTF_8);
            Files.writeString(inputDirectory.resolve("config/application.properties"),
                    "service.name=inventory\n", StandardCharsets.UTF_8);

            Path archivePath = workDirectory.resolve("dist/inventory.zip");
            Files.createDirectories(archivePath.getParent());
            Project project = new Project();
            project.init();

            FileSet files = new FileSet();
            files.setProject(project);
            files.setDir(inputDirectory.toFile());
            Zip zip = new Zip();
            zip.setProject(project);
            zip.setDestFile(archivePath.toFile());
            zip.setFilesonly(true);
            zip.addFileset(files);
            zip.execute();

            ZipFile archive = new ZipFile(archivePath.toFile());
            try {
                ZipEntry readme = archive.getEntry("docs/readme.txt");
                ZipEntry properties = archive.getEntry("config/application.properties");
                assertNotNull(readme);
                assertNotNull(properties);
                assertEquals("Inventory service\n", read(archive, readme));
                assertEquals("service.name=inventory\n", read(archive, properties));
            } finally {
                archive.close();
            }
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static String read(ZipFile archive, ZipEntry entry) throws IOException {
        try (InputStream stream = archive.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ZipArchivePackagingTest::delete);
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
