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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JUnitTaskTest {
    @TempDir
    Path directory;

    @Test
    void executesAnInProcessSuiteAndPublishesAnXmlReport() throws Exception {
        ConsumerCase.executions.set(0);
        Project project = new Project();
        project.init();
        project.setBaseDir(directory.toFile());
        JUnitTask task = new JUnitTask();
        task.setProject(project);
        // Use application classes without scanning physical JARs for Ant's implicit classpath.
        task.setIncludeantruntime(false);
        task.setFork(false);
        task.setHaltonerror(true);
        task.setHaltonfailure(true);
        task.setErrorProperty("suite.error");
        task.setFailureProperty("suite.failure");

        FormatterElement formatter = new FormatterElement();
        FormatterElement.TypeAttribute type = new FormatterElement.TypeAttribute();
        type.setValue("xml");
        formatter.setType(type);
        task.addFormatter(formatter);
        JUnitTest suite = new JUnitTest(ConsumerSuite.class.getName());
        suite.setTodir(directory.toFile());
        suite.setOutfile("consumer-results");
        task.addTest(suite);

        task.execute();

        assertThat(ConsumerCase.executions.get()).isEqualTo(1);
        assertThat(project.getProperty("suite.error")).isNull();
        assertThat(project.getProperty("suite.failure")).isNull();
        assertThat(Files.readString(directory.resolve("consumer-results.xml")))
                .contains("testConsumerCheck", "tests=\"1\"", "failures=\"0\"", "errors=\"0\"");
    }

    @Test
    void executesASuiteWithAnEmptyModulePathUsingApplicationClasses() throws Exception {
        ConsumerCase.executions.set(0);
        Project project = new Project();
        project.init();
        project.setBaseDir(directory.toFile());
        JUnitTask task = new JUnitTask();
        task.setProject(project);
        // Use application classes without scanning physical JARs for Ant's implicit classpath.
        task.setFork(false);
        task.setIncludeantruntime(false);
        task.createModulepath();
        task.setHaltonerror(true);
        task.setHaltonfailure(true);

        FormatterElement formatter = new FormatterElement();
        FormatterElement.TypeAttribute type = new FormatterElement.TypeAttribute();
        type.setValue("xml");
        formatter.setType(type);
        task.addFormatter(formatter);
        JUnitTest suite = new JUnitTest(ConsumerSuite.class.getName());
        suite.setTodir(directory.toFile());
        suite.setOutfile("modulepath-results");
        task.addTest(suite);

        task.execute();

        assertThat(ConsumerCase.executions.get()).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("modulepath-results.xml")))
                .contains("testConsumerCheck", "tests=\"1\"", "failures=\"0\"", "errors=\"0\"");
    }

    public static class ConsumerSuite {
        public static TestSuite suite() {
            TestSuite suite = new TestSuite("consumer suite");
            suite.addTest(new ConsumerCase());
            return suite;
        }
    }

    public static class ConsumerCase extends TestCase {
        static final AtomicInteger executions = new AtomicInteger();

        public ConsumerCase() {
            super("testConsumerCheck");
        }

        public void testConsumerCheck() {
            assertEquals("ANT", "ant".toUpperCase(Locale.ROOT));
            executions.incrementAndGet();
        }
    }
}
