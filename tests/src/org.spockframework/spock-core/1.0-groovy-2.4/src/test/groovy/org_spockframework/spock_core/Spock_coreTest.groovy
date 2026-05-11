/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_spockframework.spock_core

import org.junit.jupiter.api.Test
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Subject
import spock.lang.Title
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static org.assertj.core.api.Assertions.assertThat

public class Spock_coreTest {
    private static final String RESTORE_PROPERTY_KEY = 'spock.core.tck.restore.property'

    @Test
    void executesSpecificationLifecycleDataTablesAndExceptionConditions() {
        LifecycleSpecification.events.clear()

        Result result = JUnitCore.runClasses(LifecycleSpecification)

        assertThat(result.failures).isEmpty()
        assertThat(result.runCount).isGreaterThanOrEqualTo(3)
        assertThat(LifecycleSpecification.events).containsSubsequence(
                'setupSpec',
                'setup',
                'data:3',
                'cleanup',
                'setup',
                'data:4',
                'cleanup',
                'setup',
                'thrown:invalid input',
                'no-exception:SPOCK',
                'cleanup',
                'cleanupSpec')
    }

    @Test
    void invokesBuiltinExtensionsForAutoCleanupAndSystemPropertyRestoration() {
        AutoCleanupSpecification.events.clear()
        String previousValue = System.getProperty(RESTORE_PROPERTY_KEY)
        System.setProperty(RESTORE_PROPERTY_KEY, 'original')

        try {
            Result cleanupResult = JUnitCore.runClasses(AutoCleanupSpecification)
            Result propertyResult = JUnitCore.runClasses(SystemPropertySpecification)

            assertThat(cleanupResult.failures).isEmpty()
            assertThat(propertyResult.failures).isEmpty()
            assertThat(AutoCleanupSpecification.events).containsExactly('feature-body', 'dispose')
            assertThat(System.getProperty(RESTORE_PROPERTY_KEY)).isEqualTo('original')
        } finally {
            if (previousValue == null) {
                System.clearProperty(RESTORE_PROPERTY_KEY)
            } else {
                System.setProperty(RESTORE_PROPERTY_KEY, previousValue)
            }
        }
    }

    @Test
    void honorsStepwiseExecutionAndDocumentationAnnotations() {
        AnnotatedStepwiseSpecification.events.clear()

        Result result = JUnitCore.runClasses(AnnotatedStepwiseSpecification)

        assertThat(result.failures).isEmpty()
        assertThat(AnnotatedStepwiseSpecification.events).containsExactly('first', 'second')
    }

    @Test
    void reportsConditionFailuresThroughJUnitResult() {
        FailingConditionSpecification.forceFailure = true
        try {
            Result result = JUnitCore.runClasses(FailingConditionSpecification)

            assertThat(result.runCount).isEqualTo(1)
            assertThat(result.failureCount).isEqualTo(1)
            assertThat(result.failures[0].exception).isInstanceOf(AssertionError)
            assertThat(result.failures[0].testHeader).contains('renders failed power assert')
        } finally {
            FailingConditionSpecification.forceFailure = false
        }
    }

    @Test
    void coordinatesAsynchronousWorkWithSpockConcurrentUtilities() {
        ExecutorService executor = Executors.newSingleThreadExecutor()
        BlockingVariable<String> blockingVariable = new BlockingVariable<String>(1)
        PollingConditions pollingConditions = new PollingConditions(timeout: 1, initialDelay: 0, delay: 0.01)
        AsyncConditions asyncConditions = new AsyncConditions(1)
        AtomicInteger observed = new AtomicInteger(0)

        try {
            Future<?> future = executor.submit({
                observed.set(42)
                blockingVariable.set('completed')
                asyncConditions.evaluate {
                    assert observed.get() == 42
                    assert blockingVariable.get() == 'completed'
                }
            } as Runnable)

            assertThat(blockingVariable.get()).isEqualTo('completed')
            pollingConditions.eventually {
                assert observed.get() == 42
            }
            future.get(1, TimeUnit.SECONDS)
            asyncConditions.await(1)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    public static class LifecycleSpecification extends Specification {
        static List<String> events = []

        @Shared
        List<String> sharedValues = []

        def setupSpec() {
            events << 'setupSpec'
        }

        def cleanupSpec() {
            events << 'cleanupSpec'
        }

        def setup() {
            events << 'setup'
        }

        def cleanup() {
            events << 'cleanup'
        }

        @Unroll
        def 'computes maximum for #left and #right'() {
            when:
            int actual = Math.max(left, right)
            sharedValues << "${left}:${right}"
            events << "data:${maximum}".toString()

            then:
            actual == maximum
            sharedValues

            where:
            left | right || maximum
            1    | 3     || 3
            4    | 2     || 4
        }

        def 'checks thrown and no-exception conditions'() {
            when:
            throw new IllegalArgumentException('invalid input')

            then:
            IllegalArgumentException exception = thrown()
            exception.message == 'invalid input'
            events << "thrown:${exception.message}".toString()

            when:
            String value = 'spock'.toUpperCase(Locale.ROOT)

            then:
            noExceptionThrown()
            value == 'SPOCK'
            events << "no-exception:${value}".toString()
        }
    }

    public static class AutoCleanupSpecification extends Specification {
        static List<String> events = []

        @AutoCleanup('dispose')
        CloseTracker tracker = new CloseTracker(events)

        def 'disposes registered resource after feature method'() {
            expect:
            !tracker.closed
            events << 'feature-body'
        }
    }

    public static class CloseTracker {
        private final List<String> events
        boolean closed

        CloseTracker(List<String> events) {
            this.events = events
        }

        void dispose() {
            closed = true
            events << 'dispose'
        }
    }

    public static class SystemPropertySpecification extends Specification {
        @RestoreSystemProperties
        def 'restores system properties changed by a feature'() {
            when:
            System.setProperty(RESTORE_PROPERTY_KEY, 'changed')

            then:
            System.getProperty(RESTORE_PROPERTY_KEY) == 'changed'
        }
    }

    @Stepwise
    @Title('Annotated stepwise specification')
    @Narrative('Exercises Spock metadata annotations while preserving feature order.')
    @Issue('https://example.invalid/spock-core-tck')
    @See('https://spockframework.org')
    @Subject(Calculator)
    public static class AnnotatedStepwiseSpecification extends Specification {
        static List<String> events = []
        private final Calculator calculator = new Calculator()

        def 'first feature records state'() {
            expect:
            calculator.add(1, 2) == 3
            events << 'first'
        }

        def 'second feature observes stepwise order'() {
            expect:
            events == ['first']
            calculator.multiply(3, 4) == 12
            events << 'second'
        }
    }

    public static class Calculator {
        int add(int left, int right) {
            left + right
        }

        int multiply(int left, int right) {
            left * right
        }
    }

    public static class FailingConditionSpecification extends Specification {
        static boolean forceFailure

        def 'renders failed power assert'() {
            given:
            String framework = 'spock'
            String module = forceFailure ? 'spock-core' : 'spock'

            expect:
            framework == module
        }
    }
}
