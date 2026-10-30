/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import junit.framework.TestCase;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTestRunner;
import org.apache.tools.ant.taskdefs.optional.junit.TearDownOnVmCrash;
import org.junit.jupiter.api.Test;

public class TearDownOnVmCrashTest {
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
