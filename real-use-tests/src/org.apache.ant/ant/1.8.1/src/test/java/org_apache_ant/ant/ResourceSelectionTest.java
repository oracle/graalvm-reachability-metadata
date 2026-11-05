/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Ant's file selection subsystem to find deployable application resources.
public class ResourceSelectionTest {
    @Test
    @Timeout(60)
    public void selectsOnlyPublicConfigurationResources() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-resource-selection-");
        try {
            Path resourceDirectory = Files.createDirectories(workDirectory.resolve("resources"));
            write(resourceDirectory.resolve("application.properties"), "service.name=inventory\n");
            write(resourceDirectory.resolve("logging.properties"), "level=INFO\n");
            write(resourceDirectory.resolve("private/credentials.properties"),
                    "password=not-for-packaging\n");
            write(resourceDirectory.resolve("readme.txt"), "Inventory service\n");

            Project project = new Project();
            project.init();
            FileSet resources = new FileSet();
            resources.setProject(project);
            resources.setDir(resourceDirectory.toFile());
            resources.setIncludes("**/*.properties");
            resources.setExcludes("private/**");

            DirectoryScanner scanner = resources.getDirectoryScanner(project);

            assertEquals(Set.of("application.properties", "logging.properties"),
                    Set.of(scanner.getIncludedFiles()));
            assertEquals(Set.of("private/credentials.properties"),
                    Set.of(scanner.getExcludedFiles()));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ResourceSelectionTest::delete);
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
