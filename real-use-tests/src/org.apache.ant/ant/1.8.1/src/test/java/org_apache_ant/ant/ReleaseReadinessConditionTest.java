/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Ant's build-condition subsystem to decide whether an application release is ready.
public class ReleaseReadinessConditionTest {
    @Test
    @Timeout(60)
    public void setsReleaseGateOnlyWhenBuildPropertiesMeetPolicy() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-release-condition-");
        try {
            Path readyBuild = writeBuildFile(workDirectory.resolve("ready-build.xml"), "stable");
            Project readyProject = loadProject(workDirectory, readyBuild);

            assertEquals("true", readyProject.getProperty("release.ready"));

            Path previewBuild = writeBuildFile(workDirectory.resolve("preview-build.xml"), "preview");
            Project previewProject = loadProject(workDirectory, previewBuild);

            assertNull(previewProject.getProperty("release.ready"));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static Path writeBuildFile(Path buildFile, String releaseChannel) throws IOException {
        Files.writeString(buildFile, """
                <?xml version="1.0"?>
                <project name="inventory-release" basedir=".">
                  <property name="release.channel" value="%s"/>
                  <property name="artifact.name" value="inventory-service"/>
                  <condition property="release.ready">
                    <and>
                      <equals arg1="${release.channel}" arg2="stable"/>
                      <contains string="${artifact.name}" substring="service"/>
                    </and>
                  </condition>
                </project>
                """.formatted(releaseChannel), StandardCharsets.UTF_8);
        return buildFile;
    }

    private static Project loadProject(Path baseDirectory, Path buildFile) {
        Project project = new Project();
        project.setBaseDir(baseDirectory.toFile());
        project.init();
        ProjectHelper.configureProject(project, buildFile.toFile());
        return project;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ReleaseReadinessConditionTest::delete);
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
