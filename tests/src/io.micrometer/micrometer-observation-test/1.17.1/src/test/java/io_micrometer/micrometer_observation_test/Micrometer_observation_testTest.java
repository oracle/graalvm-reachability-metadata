/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation_test;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.InvalidObservationException;
import io.micrometer.observation.tck.ObservationContextAssert;
import io.micrometer.observation.tck.ObservationRegistryAssert;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Micrometer_observation_testTest {
    @Test
    void recordsACompleteObservationAndExposesFluentAssertions() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        IOException failure = new IOException("remote service unavailable");
        Observation observation = Observation.createNotStarted("orders.request", registry)
                .contextualName("GET /orders/{id}")
                .lowCardinalityKeyValue("method", "GET")
                .highCardinalityKeyValue("order.id", "42");
        observation.getContext().put("attempt", 2);

        observation.start();
        try (Observation.Scope scope = observation.openScope()) {
            ObservationRegistryAssert.assertThat(registry)
                    .hasRemainingCurrentObservationSameAs(observation)
                    .hasRemainingCurrentScopeSameAs(scope);
            observation.event(Observation.Event.of("response.received", "response accepted"));
            observation.error(failure);
        }
        observation.stop();

        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsEqualTo(1)
                .hasNumberOfObservationsWithNameEqualTo("orders.request", 1)
                .hasNumberOfObservationsWithNameEqualToIgnoreCase("ORDERS.REQUEST", 1)
                .hasAnObservationWithAKeyValue("method", "GET")
                .hasAnObservationWithAKeyName("order.id")
                .hasSingleObservationThat()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasNameEqualTo("orders.request")
                .hasContextualNameEqualTo("GET /orders/{id}")
                .hasLowCardinalityKeyValue(KeyValue.of("method", "GET"))
                .hasHighCardinalityKeyValue(KeyValue.of("order.id", "42"))
                .hasMapEntry("attempt", 2)
                .hasError(failure)
                .hasEvent("response.received", "response accepted")
                .doesNotHaveEvent("request.retried")
                .doesNotHaveParentObservation()
                .backToTestObservationRegistry()
                .hasHandledContextsThatSatisfy(contexts -> assertThat(contexts)
                        .singleElement()
                        .extracting(Observation.Context::getName)
                        .isEqualTo("orders.request"));

        ObservationRegistryAssert.assertThat(registry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .doesNotHaveAnyRemainingCurrentScope();
    }

    @Test
    void findsAndChecksMultipleRecordedObservations() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        recordObservation(registry, "cache.lookup", "result", "hit");
        recordObservation(registry, "cache.lookup", "result", "miss");
        recordObservation(registry, "cache.refresh", "result", "stored");
        AtomicInteger matchingContexts = new AtomicInteger();

        TestObservationRegistryAssert.assertThat(registry)
                .hasNumberOfObservationsEqualTo(3)
                .hasNumberOfObservationsWithNameEqualTo("cache.lookup", 2)
                .hasObservationWithNameEqualToIgnoringCase("CACHE.REFRESH")
                .that()
                .hasLowCardinalityKeyValue("result", "stored")
                .backToTestObservationRegistry()
                .forAllObservationsWithNameEqualTo("cache.lookup", context -> {
                    context.hasNameEqualTo("cache.lookup").hasLowCardinalityKeyValueWithKey("result");
                    matchingContexts.incrementAndGet();
                })
                .hasAnObservation(context -> context.hasNameEqualTo("cache.lookup")
                        .hasLowCardinalityKeyValue("result", "miss"));

        assertThat(matchingContexts).hasValue(2);
        registry.clear();
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation();
    }

    @Test
    void observationContextAssertionsCoverKeysStateErrorsAndParents() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        Observation parent = Observation.start("batch.parent", registry);
        Observation child;
        try (Observation.Scope ignored = parent.openScope()) {
            child = Observation.createNotStarted("batch.child", registry)
                    .contextualName("process batch")
                    .lowCardinalityKeyValue("job", "import")
                    .highCardinalityKeyValue("batch.id", "b-17")
                    .start();
            child.stop();
        }
        parent.stop();

        ObservationContextAssert.assertThat(child.getContextView())
                .hasNameEqualToIgnoringCase("BATCH.CHILD")
                .doesNotHaveNameEqualTo("other")
                .hasContextualNameEqualToIgnoringCase("PROCESS BATCH")
                .doesNotHaveContextualNameEqualTo("other batch")
                .hasAnyKeyValues()
                .hasKeyValuesCount(2)
                .hasOnlyKeys("job", "batch.id")
                .hasSubsetOfKeys("job", "batch.id", "unused")
                .hasLowCardinalityKeyValue("job", "import")
                .doesNotHaveLowCardinalityKeyValue("job", "export")
                .doesNotHaveLowCardinalityKeyValueWithKey("missing")
                .hasHighCardinalityKeyValue("batch.id", "b-17")
                .doesNotHaveHighCardinalityKeyValue("batch.id", "b-18")
                .doesNotHaveHighCardinalityKeyValueWithKey("missing")
                .doesNotHaveMapEntry("attempt", 3)
                .doesNotHaveError()
                .hasParentObservation()
                .hasParentObservationEqualTo(parent)
                .hasParentObservationContextMatching(context -> "batch.parent".equals(context.getName()),
                        "parent name")
                .hasParentObservationContextSatisfying(context -> assertThat(context.getName())
                        .isEqualTo("batch.parent"));
    }

    @Test
    void validatorReportsInvalidLifecycleWithContextAndHistory() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        Observation observation = Observation.createNotStarted("duplicate.start", registry).start();

        assertThatThrownBy(observation::start)
                .isInstanceOfSatisfying(InvalidObservationException.class, exception -> {
                    assertThat(exception.getMessage()).contains("has already been started");
                    assertThat(exception.getContext()).isSameAs(observation.getContext());
                    assertThat(exception.getHistory())
                            .extracting(InvalidObservationException.HistoryElement::getEventName)
                            .containsExactly(InvalidObservationException.EventName.START,
                                    InvalidObservationException.EventName.START);
                });
    }

    @Test
    void optionalValidatorEnforcesConsistentLowCardinalityKeysAndClearResetsIt() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
                .validateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeys(true)
                .build();
        recordObservation(registry, "http.request", "method", "GET");
        recordObservation(registry, "http.request", "method", "POST");

        assertThatThrownBy(() -> recordObservation(registry, "http.request", "status", "200"))
                .isInstanceOf(InvalidObservationException.class)
                .hasMessageContaining("consistent set of low cardinality keys")
                .hasMessageContaining("method")
                .hasMessageContaining("status");

        registry.clear();
        assertThatCode(() -> recordObservation(registry, "http.request", "status", "204"))
                .doesNotThrowAnyException();
        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasLowCardinalityKeyValue("status", "204")
                .hasBeenStopped();
    }

    @Test
    void registryAssertionsTrackNestedScopes() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
                .validateScopesClosedInReverseOrderOfOpening(true)
                .validateScopesOpenedAndClosedOnTheSameThread(true)
                .build();
        Observation parent = Observation.start("scope.parent", registry);
        Observation.Scope parentScope = parent.openScope();
        Observation child = Observation.start("scope.child", registry);
        Observation.Scope childScope = child.openScope();

        ObservationRegistryAssert.assertThat(registry)
                .hasRemainingCurrentObservation()
                .hasRemainingCurrentObservationSameAs(child)
                .doesNotHaveRemainingCurrentObservationSameAs(parent)
                .hasRemainingCurrentScope()
                .hasRemainingCurrentScopeSameAs(childScope)
                .doesNotHaveRemainingCurrentScopeSameAs(parentScope);

        childScope.close();
        child.stop();
        ObservationRegistryAssert.assertThat(registry)
                .hasRemainingCurrentObservationSameAs(parent)
                .hasRemainingCurrentScopeSameAs(parentScope);
        parentScope.close();
        parent.stop();
        ObservationRegistryAssert.assertThat(registry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .doesNotHaveAnyRemainingCurrentScope();
    }

    private static void recordObservation(TestObservationRegistry registry, String name, String key, String value) {
        Observation observation = Observation.createNotStarted(name, registry)
                .lowCardinalityKeyValue(key, value)
                .start();
        observation.stop();
    }
}
