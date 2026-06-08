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
import org.junit.runner.notification.Failure
import spock.lang.AutoCleanup
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Title
import spock.lang.Unroll

import static org.assertj.core.api.Assertions.assertThat

public class Spock_coreTest {
    static final List<String> fixtureEvents = []
    static final List<String> observedDataResults = []
    static final List<String> autoCleanupEvents = []
    static final List<String> sharedFieldEvents = []

    @Test
    void runsFixtureLifecycleAndDataDrivenSpecifications() {
        fixtureEvents.clear()
        observedDataResults.clear()

        Result result = JUnitCore.runClasses(FixtureLifecycleSpec, DataDrivenSpec)

        assertSuccessful(result)
        assertThat(fixtureEvents).containsExactly(
                'setupSpec',
                'setup', 'first feature', 'cleanup',
                'setup', 'second feature', 'cleanup',
                'cleanupSpec')
        assertThat(observedDataResults).containsExactly(
                '1 + 2 = 3',
                '2 + 3 = 5',
                '3 + 5 = 8')
    }

    @Test
    void runsInteractionBasedMockingAndStubbingSpecifications() {
        Result result = JUnitCore.runClasses(InteractionSpec, StubbedCollaboratorSpec)

        assertSuccessful(result)
    }

    @Test
    void runsExceptionAndConditionSpecifications() {
        Result result = JUnitCore.runClasses(ExceptionConditionSpec, CollectionConditionSpec)

        assertSuccessful(result)
    }

    @Test
    void runsAutoCleanupSpecification() {
        autoCleanupEvents.clear()

        Result result = JUnitCore.runClasses(AutoCleanupSpec)

        assertSuccessful(result)
        assertThat(autoCleanupEvents).containsExactly(
                'used managed-resource for request',
                'released managed-resource')
    }

    @Test
    void runsSharedFieldSpecification() {
        sharedFieldEvents.clear()

        Result result = JUnitCore.runClasses(SharedFieldSpec)

        assertSuccessful(result)
        assertThat(sharedFieldEvents).containsExactly(
                'first feature shared=1 instance=1',
                'second feature shared=2 instance=1')
    }

    private static void assertSuccessful(Result result) {
        List<String> failures = result.failures.collect { Failure failure ->
            "${failure.testHeader}: ${failure.message}".toString()
        }
        assertThat(failures).isEmpty()
        assertThat(result.wasSuccessful()).isTrue()
    }

    @Stepwise
    @Title('Fixture lifecycle specification')
    @Narrative('Exercises setupSpec, setup, cleanup, cleanupSpec, and feature execution ordering.')
    public static class FixtureLifecycleSpec extends Specification {
        def setupSpec() {
            Spock_coreTest.fixtureEvents << 'setupSpec'
        }

        def setup() {
            Spock_coreTest.fixtureEvents << 'setup'
        }

        def cleanup() {
            Spock_coreTest.fixtureEvents << 'cleanup'
        }

        def cleanupSpec() {
            Spock_coreTest.fixtureEvents << 'cleanupSpec'
        }

        def 'first feature records its body'() {
            expect:
            Spock_coreTest.fixtureEvents << 'first feature'
        }

        def 'second feature records its body'() {
            expect:
            Spock_coreTest.fixtureEvents << 'second feature'
        }
    }

    public static class DataDrivenSpec extends Specification {
        @Unroll
        def 'adds #left and #right to make #sum'() {
            expect:
            left + right == sum

            cleanup:
            Spock_coreTest.observedDataResults << "${left} + ${right} = ${sum}".toString()

            where:
            left | right || sum
            1    | 2     || 3
            2    | 3     || 5
            3    | 5     || 8
        }
    }

    public static class InteractionSpec extends Specification {
        def 'mock verifies interaction counts, argument constraints, and responses'() {
            given:
            GreetingService greetingService = Mock()

            when:
            String message = greetingService.greet('Ada')
            greetingService.recordVisit('Grace')

            then:
            1 * greetingService.greet('Ada') >> 'Hello Ada'
            1 * greetingService.recordVisit({ String name -> name.startsWith('Gra') })
            0 * _
            message == 'Hello Ada'
        }
    }

    public static class StubbedCollaboratorSpec extends Specification {
        def 'stub supplies deterministic values to production style collaborator code'() {
            given:
            PriceCatalog catalog = Stub()
            catalog.priceOf('book') >> 12G
            catalog.priceOf('pen') >> 3G
            Checkout checkout = new Checkout(catalog)

            expect:
            checkout.total(['book', 'pen', 'pen']) == 18G
        }
    }

    public static class ExceptionConditionSpec extends Specification {
        def 'thrown captures exception type and message'() {
            when:
            parsePositiveInteger('-7')

            then:
            IllegalArgumentException exception = thrown()
            exception.message == 'value must be positive: -7'
        }

        def 'notThrown confirms valid input path'() {
            when:
            Integer value = parsePositiveInteger('21')

            then:
            notThrown(IllegalArgumentException)
            value == 21
        }

        private static Integer parsePositiveInteger(String value) {
            Integer parsed = Integer.valueOf(value)
            if (parsed <= 0) {
                throw new IllegalArgumentException("value must be positive: ${value}".toString())
            }
            return parsed
        }
    }

    public static class CollectionConditionSpec extends Specification {
        def 'conditions support Groovy collection operations in feature methods'() {
            given:
            List<String> names = ['ada', 'grace', 'alan']

            expect:
            names.findAll { String name -> name.contains('a') }
                    .collect { String name -> name.capitalize() } == ['Ada', 'Grace', 'Alan']
            names.groupBy { String name -> name.length() }[5] == ['grace']
        }
    }

    public static class AutoCleanupSpec extends Specification {
        @AutoCleanup('release')
        ManagedResource resource = new ManagedResource('managed-resource')

        def 'auto cleanup invokes configured resource method after feature completion'() {
            expect:
            resource.useFor('request') == 'managed-resource:request'
        }
    }

    @Stepwise
    public static class SharedFieldSpec extends Specification {
        @Shared
        List<String> sharedNames = []
        List<String> instanceNames = []

        def 'first feature mutates shared and instance fields'() {
            when:
            sharedNames << 'first'
            instanceNames << 'first'

            then:
            sharedNames == ['first']
            instanceNames == ['first']

            cleanup:
            Spock_coreTest.sharedFieldEvents <<
                    "first feature shared=${sharedNames.size()} instance=${instanceNames.size()}".toString()
        }

        def 'second feature observes shared state and fresh instance state'() {
            when:
            sharedNames << 'second'
            instanceNames << 'second'

            then:
            sharedNames == ['first', 'second']
            instanceNames == ['second']

            cleanup:
            Spock_coreTest.sharedFieldEvents <<
                    "second feature shared=${sharedNames.size()} instance=${instanceNames.size()}".toString()
        }
    }

    public static interface GreetingService {
        String greet(String name)

        void recordVisit(String name)
    }

    public static interface PriceCatalog {
        BigDecimal priceOf(String item)
    }

    public static class Checkout {
        private final PriceCatalog catalog

        Checkout(PriceCatalog catalog) {
            this.catalog = catalog
        }

        BigDecimal total(List<String> items) {
            return items.collect { String item -> catalog.priceOf(item) }.sum() as BigDecimal
        }
    }

    public static class ManagedResource {
        private final String name

        ManagedResource(String name) {
            this.name = name
        }

        String useFor(String purpose) {
            Spock_coreTest.autoCleanupEvents << "used ${name} for ${purpose}".toString()
            return "${name}:${purpose}".toString()
        }

        void release() {
            Spock_coreTest.autoCleanupEvents << "released ${name}".toString()
        }
    }
}
