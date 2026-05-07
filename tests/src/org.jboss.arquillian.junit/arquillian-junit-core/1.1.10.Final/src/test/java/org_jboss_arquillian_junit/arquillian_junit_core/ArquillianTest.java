/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_junit.arquillian_junit_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.event.BeforeRules;
import org.jboss.arquillian.test.spi.LifecycleMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.test.spi.TestRunnerAdaptorBuilder;
import org.jboss.arquillian.test.spi.event.suite.TestLifecycleEvent;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class ArquillianTest {
    @Test
    public void methodBlockBuildsTheJUnitRuleStatementThroughTheArquillianRunner() throws Exception {
        RecordingAdaptor adaptor = new RecordingAdaptor();
        ManagedSubject.reset();
        TestRunnerAdaptorBuilder.set(adaptor);
        try {
            Result result = new JUnitCore().run(new ManagedSubjectRunner());

            assertThat(result.wasSuccessful()).as(result.getFailures().toString()).isTrue();
            assertThat(adaptor.testInvocations).isEqualTo(1);
            assertThat(adaptor.beforeRulesInvocations).isEqualTo(1);
            assertThat(ManagedSubject.ruleApplications).isEqualTo(1);
            assertThat(ManagedSubject.testExecutions).isEqualTo(1);
        } finally {
            TestRunnerAdaptorBuilder.set(null);
        }
    }

    public static class ManagedSubject {
        private static int ruleApplications;
        private static int testExecutions;

        @Rule
        public final TestRule countingRule = new TestRule() {
            @Override
            public Statement apply(final Statement base, Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        ruleApplications++;
                        base.evaluate();
                    }
                };
            }
        };

        public void executesThroughArquillian() {
            testExecutions++;
        }

        private static void reset() {
            ruleApplications = 0;
            testExecutions = 0;
        }
    }

    public static class ManagedSubjectRunner extends Arquillian {
        private static final FrameworkMethod MANAGED_METHOD = new FrameworkMethod(findManagedMethod());

        public ManagedSubjectRunner() throws InitializationError {
            super(ManagedSubject.class);
        }

        @Override
        protected List<FrameworkMethod> computeTestMethods() {
            return Collections.singletonList(MANAGED_METHOD);
        }

        private static Method findManagedMethod() {
            try {
                return ManagedSubject.class.getMethod("executesThroughArquillian");
            } catch (NoSuchMethodException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private static final class RecordingAdaptor implements TestRunnerAdaptor {
        private int testInvocations;
        private int beforeRulesInvocations;

        @Override
        public void beforeSuite() {
        }

        @Override
        public void afterSuite() {
        }

        @Override
        public void beforeClass(Class<?> testClass, LifecycleMethodExecutor executor) throws Exception {
            invokeLifecycle(executor);
        }

        @Override
        public void afterClass(Class<?> testClass, LifecycleMethodExecutor executor) throws Exception {
            invokeLifecycle(executor);
        }

        @Override
        public void before(Object testInstance, Method testMethod, LifecycleMethodExecutor executor) throws Exception {
            invokeLifecycle(executor);
        }

        @Override
        public void after(Object testInstance, Method testMethod, LifecycleMethodExecutor executor) throws Exception {
            invokeLifecycle(executor);
        }

        @Override
        public TestResult test(TestMethodExecutor executor) {
            testInvocations++;
            try {
                executor.invoke();
                return TestResult.passed();
            } catch (Throwable throwable) {
                return TestResult.failed(throwable);
            }
        }

        @Override
        public <T extends TestLifecycleEvent> void fireCustomLifecycle(T event) throws Exception {
            if (event instanceof BeforeRules) {
                beforeRulesInvocations++;
                invokeLifecycle(event.getExecutor());
            }
        }

        @Override
        public void shutdown() {
        }

        private static void invokeLifecycle(LifecycleMethodExecutor executor) throws Exception {
            try {
                executor.invoke();
            } catch (Exception exception) {
                throw exception;
            } catch (Error error) {
                throw error;
            } catch (Throwable throwable) {
                throw new Exception(throwable);
            }
        }
    }
}
