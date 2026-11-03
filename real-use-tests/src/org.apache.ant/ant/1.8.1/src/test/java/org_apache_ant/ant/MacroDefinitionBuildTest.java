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

/// Exercises Ant's macro definition subsystem to reuse a release packaging step.
public class MacroDefinitionBuildTest {
    @Test
    @Timeout(60)
    public void invokesParameterizedPackagingMacroThroughBuildFile() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-macro-definition-");
        try {
            Path buildFile = workDirectory.resolve("build.xml");
            Files.writeString(buildFile, """
                    <?xml version="1.0"?>
                    <project name="inventory-release" default="package" basedir=".">
                      <macrodef name="write-release-properties">
                        <attribute name="service"/>
                        <attribute name="release"/>
                        <sequential>
                          <mkdir dir="dist"/>
                          <echo file="dist/@{service}.properties"
                                message="service.name=@{service}&#10;release=@{release}&#10;"/>
                        </sequential>
                      </macrodef>
                      <target name="package">
                        <write-release-properties service="inventory" release="2026.04"/>
                      </target>
                    </project>
                    """, StandardCharsets.UTF_8);

            Project project = new Project();
            project.setBaseDir(workDirectory.toFile());
            project.init();
            ProjectHelper.configureProject(project, buildFile.toFile());
            project.executeTarget("package");

            assertEquals("service.name=inventory\nrelease=2026.04\n",
                    Files.readString(workDirectory.resolve("dist/inventory.properties"),
                            StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(MacroDefinitionBuildTest::delete);
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
