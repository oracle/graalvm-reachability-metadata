/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.PlainJUnitResultFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FormatterElementTest {
    @TempDir
    Path directory;

    @Test
    void createsBuiltInFormattersAndWritesTheirReports() throws Exception {
        for (String type : new String[]{"plain", "brief", "xml"}) {
            FormatterElement formatter = new FormatterElement();
            FormatterElement.TypeAttribute attribute = new FormatterElement.TypeAttribute();
            attribute.setValue(type);
            formatter.setType(attribute);
            runWith(formatter, type);

            String report = Files.readString(directory.resolve(type + formatter.getExtension()));
            assertThat(report).contains("ConsumerSuite");
            if ("xml".equals(type)) {
                assertThat(report).contains("testConsumerCheck", "tests=\"1\"", "failures=\"0\"", "errors=\"0\"");
            } else {
                assertThat(report).contains("Tests run: 1", "Failures: 0", "Errors: 0");
            }
        }
    }

    @Test
    void suppliesTheProjectToACustomFormatter() throws Exception {
        FormatterElement formatter = new FormatterElement();
        formatter.setClassname(ProjectAwareFormatter.class.getName());
        formatter.setExtension(".txt");
        Project expected = new Project();
        formatter.setProject(expected);

        runWith(formatter, "project-aware");

        assertThat(ProjectAwareFormatter.latest.project).isSameAs(expected);
        assertThat(ProjectAwareFormatter.latest.setterCalls).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("project-aware.txt"))).contains("Tests run: 1");
    }

    @Test
    void preservesAnExistingFormatterProject() throws Exception {
        FormatterElement formatter = new FormatterElement();
        formatter.setClassname(PreconfiguredFormatter.class.getName());
        formatter.setExtension(".txt");
        formatter.setProject(new Project());

        runWith(formatter, "preconfigured");

        assertThat(ProjectAwareFormatter.latest.project).isSameAs(PreconfiguredFormatter.ORIGINAL_PROJECT);
        assertThat(ProjectAwareFormatter.latest.setterCalls).isZero();
        assertThat(Files.readString(directory.resolve("preconfigured.txt"))).contains("Tests run: 1");
    }

    @Test
    void loadsACustomFormatterWithTheConfiguredTaskClassloader() throws Exception {
        FormatterElement formatter = new FormatterElement();
        formatter.setClassname(ProjectAwareFormatter.class.getName());
        formatter.setExtension(".txt");
        Project expected = new Project();
        formatter.setProject(expected);

        runWith(formatter, "task-classloader", true);

        assertThat(ProjectAwareFormatter.latest.project).isSameAs(expected);
        assertThat(ProjectAwareFormatter.latest.setterCalls).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("task-classloader.txt")))
                .contains("Tests run: 1", "Failures: 0", "Errors: 0");
    }

    private void runWith(FormatterElement formatter, String outputName) throws Exception {
        runWith(formatter, outputName, false);
    }

    private void runWith(FormatterElement formatter, String outputName, boolean configureClasspath) throws Exception {
        Project project = new Project();
        project.init();
        project.setBaseDir(directory.toFile());
        JUnitTask task = new JUnitTask();
        task.setProject(project);
        // Use application classes without scanning physical JARs for Ant's implicit classpath.
        task.setIncludeantruntime(false);
        task.setFork(false);
        if (configureClasspath) {
            task.createClasspath();
        }
        task.setHaltonerror(true);
        task.setHaltonfailure(true);
        JUnitTest suite = new JUnitTest(JUnitTaskTest.ConsumerSuite.class.getName());
        suite.setTodir(directory.toFile());
        suite.setOutfile(outputName);
        task.addTest(suite);
        task.addFormatter(formatter);
        task.execute();
    }

    public static class ProjectAwareFormatter extends PlainJUnitResultFormatter {
        static ProjectAwareFormatter latest;
        public Project project;
        int setterCalls;

        public ProjectAwareFormatter() {
            latest = this;
        }

        public void setProject(Project value) {
            project = value;
            setterCalls++;
        }
    }

    public static class PreconfiguredFormatter extends ProjectAwareFormatter {
        static final Project ORIGINAL_PROJECT = new Project();

        public PreconfiguredFormatter() {
            project = ORIGINAL_PROJECT;
        }
    }
}
