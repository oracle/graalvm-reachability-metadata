/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.junit.jupiter.api.Test;
import org.testng.IConfigurationListener;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.ITestResultNotifier;
import org.testng.internal.InvokedMethod;
import org.testng.junit.JUnitTestRunner;
import org.testng.xml.XmlTest;

public class JUnitTestRunnerTest {
    @Test
    void runsNamedJUnit3MethodsThroughStringConstructor() {
        RecordingNotifier notifier = new RecordingNotifier();
        JUnitTestRunner runner = new JUnitTestRunner(notifier);

        TestResult result = runner.start(ConstructorBasedJUnitTestCase.class, "testSelectedMethod");

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(ConstructorBasedJUnitTestCase.executedNames).containsExactly("testSelectedMethod");
        assertThat(runner.getTestMethods()).hasSize(1);
        assertThat(notifier.passedTestCount()).isEqualTo(1);
        assertThat(notifier.invokedMethods).hasSize(1);
    }

    @Test
    void runsJUnitSuiteFactoryMethod() {
        RecordingNotifier notifier = new RecordingNotifier();
        JUnitTestRunner runner = new JUnitTestRunner(notifier);

        TestResult result = runner.start(SuiteBasedJUnitTestCase.class);

        assertThat(result.runCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(SuiteBasedJUnitTestCase.executedNames).containsExactly("testFromSuite");
        assertThat(runner.getTestMethods()).hasSize(1);
        assertThat(notifier.passedTestCount()).isEqualTo(1);
        assertThat(notifier.invokedMethods).hasSize(1);
    }

    public static final class ConstructorBasedJUnitTestCase extends TestCase {
        private static final List<String> executedNames = new ArrayList<>();

        public ConstructorBasedJUnitTestCase(String name) {
            super(name);
        }

        @Override
        protected void setUp() {
            executedNames.clear();
        }

        public void testSelectedMethod() {
            executedNames.add(getName());
        }
    }

    public static final class SuiteBasedJUnitTestCase extends TestCase {
        private static final List<String> executedNames = new ArrayList<>();

        public SuiteBasedJUnitTestCase(String name) {
            super(name);
        }

        public static TestSuite suite() {
            TestSuite suite = new TestSuite();
            suite.addTest(new SuiteBasedJUnitTestCase("testFromSuite"));
            return suite;
        }

        @Override
        protected void setUp() {
            executedNames.clear();
        }

        public void testFromSuite() {
            executedNames.add(getName());
        }
    }

    private static final class RecordingNotifier implements ITestResultNotifier {
        private final Set<ITestResult> passedTests = new LinkedHashSet<>();
        private final Set<ITestResult> failedTests = new LinkedHashSet<>();
        private final Set<ITestResult> skippedTests = new LinkedHashSet<>();
        private final List<InvokedMethod> invokedMethods = new ArrayList<>();

        @Override
        public Set<ITestResult> getPassedTests(ITestNGMethod tm) {
            return new HashSet<>(passedTests);
        }

        @Override
        public Set<ITestResult> getFailedTests(ITestNGMethod tm) {
            return new HashSet<>(failedTests);
        }

        @Override
        public Set<ITestResult> getSkippedTests(ITestNGMethod tm) {
            return new HashSet<>(skippedTests);
        }

        @Override
        public void addPassedTest(ITestNGMethod tm, ITestResult tr) {
            passedTests.add(tr);
        }

        @Override
        public void addSkippedTest(ITestNGMethod tm, ITestResult tr) {
            skippedTests.add(tr);
        }

        @Override
        public void addFailedTest(ITestNGMethod tm, ITestResult tr) {
            failedTests.add(tr);
        }

        @Override
        public void addFailedButWithinSuccessPercentageTest(ITestNGMethod tm, ITestResult tr) {
            failedTests.add(tr);
        }

        @Override
        public void addInvokedMethod(InvokedMethod im) {
            invokedMethods.add(im);
        }

        @Override
        public XmlTest getTest() {
            return new XmlTest();
        }

        @Override
        public List<ITestListener> getTestListeners() {
            return Collections.emptyList();
        }

        @Override
        public List<IConfigurationListener> getConfigurationListeners() {
            return Collections.emptyList();
        }

        private int passedTestCount() {
            return passedTests.size();
        }
    }
}
