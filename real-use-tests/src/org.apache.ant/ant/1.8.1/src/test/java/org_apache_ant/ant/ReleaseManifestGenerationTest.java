/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Manifest;
import org.apache.tools.ant.taskdefs.ManifestTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Ant's manifest generation subsystem to publish release identity for an application.
public class ReleaseManifestGenerationTest {
    @Test
    @Timeout(60)
    public void writesReleaseManifestWithApplicationIdentity() throws Exception {
        Path workDirectory = Files.createTempDirectory("ant-release-manifest-");
        try {
            Path manifestPath = workDirectory.resolve("dist/MANIFEST.MF");
            Files.createDirectories(manifestPath.getParent());

            Project project = new Project();
            project.init();
            ManifestTask manifestTask = new ManifestTask();
            manifestTask.setProject(project);
            manifestTask.setFile(manifestPath.toFile());
            manifestTask.addConfiguredAttribute(attribute("Manifest-Version", "1.0"));
            manifestTask.addConfiguredAttribute(attribute("Main-Class", "com.example.InventoryApplication"));
            manifestTask.addConfiguredAttribute(attribute("Implementation-Version", "2026.04"));

            Manifest.Section deployment = new Manifest.Section();
            deployment.setName("Deployment");
            deployment.addConfiguredAttribute(attribute("Environment", "production"));
            deployment.addConfiguredAttribute(attribute("Artifact", "inventory-service.jar"));
            manifestTask.addConfiguredSection(deployment);
            manifestTask.execute();

            assertNotNull(Files.readString(manifestPath));
            try (var stream = Files.newInputStream(manifestPath)) {
                java.util.jar.Manifest manifest = new java.util.jar.Manifest(stream);
                assertEquals("1.0", manifest.getMainAttributes().getValue("Manifest-Version"));
                assertEquals("com.example.InventoryApplication",
                        manifest.getMainAttributes().getValue("Main-Class"));
                assertEquals("2026.04",
                        manifest.getMainAttributes().getValue("Implementation-Version"));
                assertEquals("production", manifest.getAttributes("Deployment").getValue("Environment"));
                assertEquals("inventory-service.jar",
                        manifest.getAttributes("Deployment").getValue("Artifact"));
            }
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Manifest.Attribute attribute(String name, String value) {
        Manifest.Attribute attribute = new Manifest.Attribute();
        attribute.setName(name);
        attribute.setValue(value);
        return attribute;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ReleaseManifestGenerationTest::delete);
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
