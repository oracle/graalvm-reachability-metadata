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

/// Exercises Ant's target dependency scheduling subsystem to build a release in dependency order.
public class TargetDependencySchedulingTest {
    @Test
    @Timeout(60)
    public void executesReleaseTargetsOnceInDependencyOrder() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-target-dependencies-");
        try {
            Path buildFile = workDirectory.resolve("build.xml");
            Files.writeString(buildFile, """
                    <?xml version="1.0"?>
                    <project name="inventory-release" default="package" basedir=".">
                      <property name="sequence.file" value="build/sequence.log"/>
                      <target name="prepare">
                        <mkdir dir="build"/>
                        <echo file="${sequence.file}" message="prepare&#10;" append="true"/>
                      </target>
                      <target name="compile" depends="prepare">
                        <echo file="${sequence.file}" message="compile&#10;" append="true"/>
                      </target>
                      <target name="package" depends="compile,prepare">
                        <echo file="${sequence.file}" message="package&#10;" append="true"/>
                      </target>
                    </project>
                    """, StandardCharsets.UTF_8);

            Project project = new Project();
            project.setBaseDir(workDirectory.toFile());
            project.init();
            ProjectHelper.configureProject(project, buildFile.toFile());
            project.executeTarget("package");

            assertEquals("inventory-release", project.getName());
            assertEquals("prepare\ncompile\npackage\n",
                    Files.readString(workDirectory.resolve("build/sequence.log"),
                            StandardCharsets.UTF_8));
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(TargetDependencySchedulingTest::delete);
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
