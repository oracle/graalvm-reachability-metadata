/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Ant's build event listener subsystem to collect application build progress.
public class BuildEventListenerTest {
    @Test
    @Timeout(60)
    public void observesBuildLifecycleWhileApplicationRunsReleaseTarget() throws IOException {
        Path workDirectory = Files.createTempDirectory("ant-build-events-");
        try {
            Path buildFile = workDirectory.resolve("build.xml");
            Files.writeString(buildFile, """
                    <?xml version="1.0"?>
                    <project name="inventory-release" default="package" basedir=".">
                      <target name="package">
                        <echo message="packaging inventory"/>
                      </target>
                    </project>
                    """, StandardCharsets.UTF_8);

            Project project = new Project();
            project.setBaseDir(workDirectory.toFile());
            project.init();
            BuildProgressListener listener = new BuildProgressListener();
            project.addBuildListener(listener);
            ProjectHelper.configureProject(project, buildFile.toFile());
            project.fireBuildStarted();
            project.executeTarget("package");
            project.fireBuildFinished(null);

            assertEquals(List.of(
                    "buildStarted",
                    "targetStarted:package",
                    "taskStarted:echo",
                    "message:packaging inventory",
                    "taskFinished:echo",
                    "targetFinished:package",
                    "buildFinished"), listener.events);
        } finally {
            deleteRecursively(workDirectory);
        }
    }

    private static final class BuildProgressListener implements BuildListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void buildStarted(BuildEvent event) {
            events.add("buildStarted");
        }

        @Override
        public void buildFinished(BuildEvent event) {
            events.add("buildFinished");
        }

        @Override
        public void targetStarted(BuildEvent event) {
            events.add("targetStarted:" + event.getTarget().getName());
        }

        @Override
        public void targetFinished(BuildEvent event) {
            events.add("targetFinished:" + event.getTarget().getName());
        }

        @Override
        public void taskStarted(BuildEvent event) {
            events.add("taskStarted:" + event.getTask().getTaskName());
        }

        @Override
        public void taskFinished(BuildEvent event) {
            events.add("taskFinished:" + event.getTask().getTaskName());
        }

        @Override
        public void messageLogged(BuildEvent event) {
            if (event.getMessage().equals("packaging inventory")) {
                events.add("message:" + event.getMessage());
            }
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(BuildEventListenerTest::delete);
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
