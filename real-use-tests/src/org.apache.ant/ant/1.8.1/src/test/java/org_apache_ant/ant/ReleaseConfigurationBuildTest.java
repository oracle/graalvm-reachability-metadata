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

/// Exercises Ant's XML build execution subsystem to render a release configuration from a template.
public class ReleaseConfigurationBuildTest {
    @Test
    @Timeout(60)
    public void rendersAReleaseConfigurationThroughTheBuildFile() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-release-build-");
        try {
            Path template = workDirectory.resolve("template.properties");
            Files.writeString(template, """
                    service.name=inventory
                    release=@release@
                    artifact=dist/inventory-@release@.zip
                    """, StandardCharsets.UTF_8);

            Path buildFile = workDirectory.resolve("build.xml");
            Files.writeString(buildFile, """
                    <?xml version="1.0"?>
                    <project name="inventory-release" default="package" basedir=".">
                      <property name="source.file" value="template.properties"/>
                      <property name="destination.file" value="dist/application.properties"/>
                      <property name="release" value="2026.04"/>
                      <target name="package">
                        <mkdir dir="dist"/>
                        <copy file="${source.file}" tofile="${destination.file}">
                          <filterset>
                            <filter token="release" value="${release}"/>
                          </filterset>
                        </copy>
                      </target>
                    </project>
                    """, StandardCharsets.UTF_8);

            Project project = new Project();
            project.setBaseDir(workDirectory.toFile());
            project.init();
            ProjectHelper.configureProject(project, buildFile.toFile());
            project.executeTarget("package");

            assertEquals("inventory-release", project.getName());
            assertEquals("service.name=inventory\nrelease=2026.04\n"
                    + "artifact=dist/inventory-2026.04.zip\n",
                    Files.readString(workDirectory.resolve("dist/application.properties"),
                            StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> delete(path));
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
