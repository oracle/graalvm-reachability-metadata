/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_junit.arquillian_junit_container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.core.spi.context.Context;
import org.jboss.arquillian.junit.State;
import org.jboss.arquillian.junit.container.JUnitContainerExtension;
import org.jboss.arquillian.junit.container.JUnitDeploymentAppender;
import org.jboss.arquillian.junit.container.JUnitTestRunner;
import org.jboss.arquillian.test.spi.TestResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.Statement;

public class Arquillian_junit_containerTest {
    @AfterEach
    void clearArquillianJUnitState() {
        State.caughtTestException(null);
        State.caughtExceptionAfterJunit(null);
    }

    @Test
    void junitTestRunnerReportsPassingMethodAndNotifiesCustomListeners() {
        RecordingJUnitTestRunner runner = new RecordingJUnitTestRunner();

        TestResult result = runner.execute(SampleJUnit4TestCase.class, "passingTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.PASSED);
        assertThat(result.getThrowable()).isNull();
        assertThat(result.getEnd()).isGreaterThanOrEqualTo(result.getStart());
        assertThat(runner.events()).containsExactly("started:passingTest", "finished:passingTest");
    }

    @Test
    void junitTestRunnerAppliesJUnitRulesAroundSelectedMethod() {
        RuleBackedJUnit4TestCase.resetEvents();

        TestResult result = new JUnitTestRunner().execute(RuleBackedJUnit4TestCase.class, "ruleAwareTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.PASSED);
        assertThat(result.getThrowable()).isNull();
        assertThat(RuleBackedJUnit4TestCase.events()).containsExactly(
                "before:ruleAwareTest",
                "test:ruleAwareTest",
                "after:ruleAwareTest");
    }

    @Test
    void junitTestRunnerRunsJUnitFixtureMethodsAroundSelectedMethod() {
        FixtureBackedJUnit4TestCase.resetEvents();

        TestResult result = new JUnitTestRunner().execute(FixtureBackedJUnit4TestCase.class, "selectedTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.PASSED);
        assertThat(result.getThrowable()).isNull();
        assertThat(FixtureBackedJUnit4TestCase.events()).containsExactly(
                "beforeClass",
                "before:selectedTest",
                "test:selectedTest",
                "after:selectedTest",
                "afterClass");
    }

    @Test
    void junitTestRunnerReportsAssertionFailuresWithThrownCause() {
        SampleJUnit4TestCase.failWhenRequestedByContainerRunner();
        try {
            TestResult result = new JUnitTestRunner().execute(SampleJUnit4TestCase.class, "failingTest");

            assertThat(result.getStatus()).isEqualTo(TestResult.Status.FAILED);
            assertThat(result.getThrowable()).isInstanceOf(AssertionError.class)
                    .hasMessage("intentional assertion failure");
            assertThat(result.getExceptionProxy()).isNotNull();
        } finally {
            SampleJUnit4TestCase.resetRequestedFailure();
        }
    }

    @Test
    void junitTestRunnerConvertsFailedAssumptionsToSkippedResults() {
        TestResult result = new JUnitTestRunner().execute(SampleJUnit4TestCase.class, "assumptionSkippedTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.SKIPPED);
        assertThat(result.getThrowable()).isNotNull();
        assertThat(result.getThrowable().getMessage()).contains("feature is not available");
    }

    @Test
    void junitTestRunnerReportsIgnoredMethodsAsSkippedResults() {
        TestResult result = new JUnitTestRunner().execute(SampleJUnit4TestCase.class, "ignoredTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.SKIPPED);
        assertThat(result.getThrowable()).isNull();
    }

    @Test
    void junitTestRunnerPreservesCapturedExpectedExceptionForClientRethrow() {
        TestResult result = new JUnitTestRunner().execute(SampleJUnit4TestCase.class, "expectedExceptionTest");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.PASSED);
        assertThat(result.getThrowable()).isInstanceOf(IllegalStateException.class)
                .hasMessage("expected in-container exception");
        assertThat(State.hasTestException()).isFalse();
    }

    @Test
    void junitTestRunnerReturnsFailedResultWhenMethodCannotBeResolved() {
        TestResult result = new JUnitTestRunner().execute(SampleJUnit4TestCase.class, "missingTestMethod");

        assertThat(result.getStatus()).isEqualTo(TestResult.Status.FAILED);
        assertThat(result.getThrowable()).isNotNull();
        assertThat(result.getThrowable().getMessage()).contains("No tests found matching Method missingTestMethod");
    }

    @Test
    void containerExtensionRegistersDeploymentArchiveAppenderService() {
        RecordingExtensionBuilder builder = new RecordingExtensionBuilder();

        new JUnitContainerExtension().register(builder);

        assertThat(builder.services()).containsExactly(
                "org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender="
                        + "org.jboss.arquillian.junit.container.JUnitDeploymentAppender");
        assertThat(builder.overrides()).isEmpty();
        assertThat(builder.observers()).isEmpty();
        assertThat(builder.contexts()).isEmpty();
    }

    public static class FixtureBackedJUnit4TestCase {
        private static final List<String> EVENTS = new ArrayList<>();

        static void resetEvents() {
            EVENTS.clear();
        }

        static List<String> events() {
            return EVENTS;
        }

        @org.junit.BeforeClass
        public static void beforeClass() {
            EVENTS.add("beforeClass");
        }

        @org.junit.AfterClass
        public static void afterClass() {
            EVENTS.add("afterClass");
        }

        @org.junit.Before
        public void before() {
            EVENTS.add("before:selectedTest");
        }

        @org.junit.After
        public void after() {
            EVENTS.add("after:selectedTest");
        }

        @org.junit.Test
        public void selectedTest() {
            EVENTS.add("test:selectedTest");
        }

        @org.junit.Test
        public void unselectedTest() {
            EVENTS.add("test:unselectedTest");
        }
    }

    public static class RuleBackedJUnit4TestCase {
        private static final List<String> EVENTS = new ArrayList<>();

        @org.junit.Rule
        public final TestRule recordingRule = (statement, description) -> new Statement() {
            @Override
            public void evaluate() throws Throwable {
                EVENTS.add("before:" + description.getMethodName());
                try {
                    statement.evaluate();
                } finally {
                    EVENTS.add("after:" + description.getMethodName());
                }
            }
        };

        static void resetEvents() {
            EVENTS.clear();
        }

        static List<String> events() {
            return EVENTS;
        }

        @org.junit.Test
        public void ruleAwareTest() {
            EVENTS.add("test:ruleAwareTest");
        }
    }

    public static class SampleJUnit4TestCase {
        private static final ThreadLocal<Boolean> FAILING_TEST_ENABLED = ThreadLocal.withInitial(() -> false);

        static void failWhenRequestedByContainerRunner() {
            FAILING_TEST_ENABLED.set(true);
        }

        static void resetRequestedFailure() {
            FAILING_TEST_ENABLED.remove();
        }

        @org.junit.Test
        public void passingTest() {
        }

        @org.junit.Test
        public void failingTest() {
            if (FAILING_TEST_ENABLED.get()) {
                throw new AssertionError("intentional assertion failure");
            }
        }

        @org.junit.Test
        public void assumptionSkippedTest() {
            org.junit.Assume.assumeTrue("feature is not available", false);
        }

        @org.junit.Ignore("covered as an ignored in-container method")
        @org.junit.Test
        public void ignoredTest() {
            throw new AssertionError("ignored methods must not be invoked");
        }

        @org.junit.Test(expected = IllegalStateException.class)
        public void expectedExceptionTest() {
            IllegalStateException exception = new IllegalStateException("expected in-container exception");
            State.caughtTestException(exception);
            throw exception;
        }
    }

    private static class RecordingJUnitTestRunner extends JUnitTestRunner {
        private final List<String> events = new ArrayList<>();

        @Override
        protected List<RunListener> getRunListeners() {
            return Collections.singletonList(new RunListener() {
                @Override
                public void testStarted(Description description) {
                    events.add("started:" + description.getMethodName());
                }

                @Override
                public void testFinished(Description description) {
                    events.add("finished:" + description.getMethodName());
                }
            });
        }

        List<String> events() {
            return events;
        }
    }

    private static class RecordingExtensionBuilder implements LoadableExtension.ExtensionBuilder {
        private final List<String> services = new ArrayList<>();
        private final List<String> overrides = new ArrayList<>();
        private final List<String> observers = new ArrayList<>();
        private final List<String> contexts = new ArrayList<>();

        @Override
        public <T> LoadableExtension.ExtensionBuilder service(Class<T> service, Class<? extends T> impl) {
            services.add(service.getName() + "=" + impl.getName());
            assertThat(service).isEqualTo(AuxiliaryArchiveAppender.class);
            assertThat(impl).isEqualTo(JUnitDeploymentAppender.class);
            return this;
        }

        @Override
        public <T> LoadableExtension.ExtensionBuilder override(Class<T> service,
                Class<? extends T> oldServiceImpl,
                Class<? extends T> newServiceImpl) {
            overrides.add(service.getName() + "=" + oldServiceImpl.getName() + "->" + newServiceImpl.getName());
            return this;
        }

        @Override
        public LoadableExtension.ExtensionBuilder observer(Class<?> handler) {
            observers.add(handler.getName());
            return this;
        }

        @Override
        public LoadableExtension.ExtensionBuilder context(Class<? extends Context> context) {
            contexts.add(context.getName());
            return this;
        }

        List<String> services() {
            return services;
        }

        List<String> overrides() {
            return overrides;
        }

        List<String> observers() {
            return observers;
        }

        List<String> contexts() {
            return contexts;
        }
    }
}
