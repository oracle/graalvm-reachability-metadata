/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTestRunner;
import org.apache.tools.ant.taskdefs.optional.junit.PlainJUnitResultFormatter;
import org.junit.jupiter.api.Test;

public class JUnitTestRunnerTest {
    @Test
    void runsASuiteFactoryWithTheDefaultLoader() {
        JUnitTaskTest.ConsumerCase.executions.set(0);
        JUnitTest suite = new JUnitTest(JUnitTaskTest.ConsumerSuite.class.getName());
        JUnitTestRunner runner = new JUnitTestRunner(suite, false, true, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PlainJUnitResultFormatter formatter = new PlainJUnitResultFormatter();
        formatter.setOutput(output);
        runner.addFormatter(formatter);

        runner.run();

        assertThat(runner.getRetCode()).isZero();
        assertThat(suite.runCount()).isEqualTo(1);
        assertThat(JUnitTaskTest.ConsumerCase.executions.get()).isEqualTo(1);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("testConsumerCheck", "Tests run: 1");
    }

    @Test
    void discoversLegacyTestMethodsWithTheApplicationLoader() {
        LegacyCase.executions.set(0);
        JUnitTest suite = new JUnitTest(LegacyCase.class.getName());
        suite.setSkipNonTests(true);
        JUnitTestRunner runner = new JUnitTestRunner(suite, false, true, false, LegacyCase.class.getClassLoader());

        runner.run();

        assertThat(runner.getRetCode()).isZero();
        assertThat(suite.runCount()).isEqualTo(1);
        assertThat(suite.errorCount()).isZero();
        assertThat(LegacyCase.executions.get()).isEqualTo(1);
    }

    @Test
    void countsAnnotatedTestsAndReportsIgnoredTests() {
        JUnit4Scenario.executions.set(0);
        JUnitTest suite = new JUnitTest(JUnit4Scenario.class.getName());
        suite.setSkipNonTests(true);
        JUnitTestRunner runner = new JUnitTestRunner(suite, false, true, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PlainJUnitResultFormatter formatter = new PlainJUnitResultFormatter();
        formatter.setOutput(output);
        runner.addFormatter(formatter);

        runner.run();

        assertThat(runner.getRetCode()).as(output.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(suite.runCount()).isEqualTo(2);
        assertThat(suite.skipCount()).isEqualTo(1);
        assertThat(suite.failureCount()).isZero();
        assertThat(suite.errorCount()).isZero();
        assertThat(JUnit4Scenario.executions.get()).isEqualTo(1);
    }

    public static class LegacyCase extends TestCase {
        static final AtomicInteger executions = new AtomicInteger();

        public void testAddition() {
            assertEquals(7, 3 + 4);
            executions.incrementAndGet();
        }
    }
}
