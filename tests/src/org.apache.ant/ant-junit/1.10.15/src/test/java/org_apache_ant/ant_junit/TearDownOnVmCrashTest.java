/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import junit.framework.TestCase;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.junit.FormatterElement;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTask;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTestRunner;
import org.apache.tools.ant.taskdefs.optional.junit.TearDownOnVmCrash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TearDownOnVmCrashTest {
    @TempDir
    Path directory;

    @Test
    void leavesOrdinaryTestCleanupToJUnitAndProducesNoReport() {
        CleanupCase.cleanupCalls = 0;
        CleanupCase.executions = 0;
        JUnitTest suite = new JUnitTest(CleanupCase.class.getName());
        TearDownOnVmCrash formatter = new TearDownOnVmCrash();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        formatter.setOutput(output);
        JUnitTestRunner runner = new JUnitTestRunner(suite, false, true, false);
        runner.addFormatter(formatter);

        runner.run();

        assertThat(runner.getRetCode()).isZero();
        assertThat(suite.runCount()).isEqualTo(1);
        assertThat(CleanupCase.executions).isEqualTo(1);
        assertThat(CleanupCase.cleanupCalls).isEqualTo(1);
        assertThat(output.size()).isZero();
    }

    @Test
    void cleansUpWhenTheForkedJvmExitsBeforeStartingTheSuite() throws Exception {
        runRecovery(CleanupCase.class.getClassLoader());
    }

    @Test
    void usesTheFormatterClassloaderWhenThereIsNoContextClassloader() throws Exception {
        runRecovery(null);
    }

    private void runRecovery(ClassLoader contextLoader) throws Exception {
        CleanupCase.cleanupCalls = 0;
        CleanupCase.executions = 0;
        Project project = new Project();
        project.init();
        project.setBaseDir(directory.toFile());
        JUnitTask task = new JUnitTask();
        task.setProject(project);
        task.init();
        task.setFork(true);
        task.setIncludeantruntime(false);
        task.setTimeout(10000);
        task.setTempdir(directory.toFile());
        task.setErrorProperty("fork.error");
        task.setFailureProperty("fork.failure");
        task.setJvm(Path.of(System.getenv("JAVA_HOME"), "bin", "java").toString());
        // The JVM exits before Ant's runner starts, exercising premature-exit recovery.
        task.createJvmarg().setValue("-version");
        FormatterElement formatter = new FormatterElement();
        formatter.setClassname(TearDownOnVmCrash.class.getName());
        formatter.setUseFile(false);
        task.addFormatter(formatter);
        task.addTest(new JUnitTest(CleanupCase.class.getName()));
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(contextLoader);
            task.execute();
        } finally {
            thread.setContextClassLoader(originalLoader);
        }

        assertThat(project.getProperty("fork.error")).isEqualTo("true");
        assertThat(project.getProperty("fork.failure")).isEqualTo("true");
        assertThat(CleanupCase.executions).isZero();
        assertThat(CleanupCase.cleanupCalls).isEqualTo(1);
    }

    public static class CleanupCase extends TestCase {
        static int cleanupCalls;
        static int executions;

        public void testLifecycle() {
            executions++;
        }

        @Override
        public void tearDown() {
            cleanupCalls++;
        }
    }
}
