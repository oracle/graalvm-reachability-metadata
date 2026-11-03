/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Tar;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Ant's TAR archive subsystem to package a release and inspect its contents.
public class TarArchivePackagingTest {
    @Test
    @Timeout(60)
    public void packagesSelectedReleaseFilesIntoTarArchive() throws Exception {
        Path workDirectory = Files.createTempDirectory("ant-tar-package-");
        try {
            Path inputDirectory = Files.createDirectories(workDirectory.resolve("release"));
            Files.createDirectories(inputDirectory.resolve("bin"));
            Files.createDirectories(inputDirectory.resolve("config"));
            Files.createDirectories(inputDirectory.resolve("private"));
            Files.writeString(inputDirectory.resolve("bin/start.sh"), "#!/bin/sh\n", StandardCharsets.UTF_8);
            Files.writeString(inputDirectory.resolve("config/application.properties"),
                    "service.name=inventory\n", StandardCharsets.UTF_8);
            Files.writeString(inputDirectory.resolve("private/credentials.properties"),
                    "password=not-for-packaging\n", StandardCharsets.UTF_8);

            Path archivePath = workDirectory.resolve("dist/inventory.tar");
            Files.createDirectories(archivePath.getParent());
            Project project = new Project();
            project.init();
            Tar tar = new Tar();
            tar.setProject(project);
            tar.setDestFile(archivePath.toFile());
            Tar.TarFileSet releaseFiles = tar.createTarFileSet();
            releaseFiles.setDir(inputDirectory.toFile());
            releaseFiles.setIncludes("bin/**,config/**");
            releaseFiles.setPrefix("inventory/");
            tar.add(releaseFiles);
            tar.execute();

            assertEquals(Map.of("inventory/bin/", "", "inventory/config/", "",
                    "inventory/bin/start.sh", "#!/bin/sh\n",
                    "inventory/config/application.properties", "service.name=inventory\n"),
                    readEntries(archivePath));
            assertFalse(readEntries(archivePath).containsKey("inventory/private/credentials.properties"));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Map<String, String> readEntries(Path archivePath) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(archivePath);
                TarInputStream archive = new TarInputStream(input)) {
            TarEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                archive.copyEntryContents(content);
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8.name()));
            }
        }
        return entries;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(TarArchivePackagingTest::delete);
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
