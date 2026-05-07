/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_health;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.health.HealthCheckResultStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.ContextHealthCheck;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.health.DefaultHealthChecksLoader;
import org.apache.camel.impl.health.ProducersHealthCheckRepository;
import org.apache.camel.impl.health.RouteControllerHealthCheck;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Camel_healthTest {
    @Test
    void contextHealthCheckReportsCamelContextLifecycleDetails() {
        DefaultCamelContext context = new DefaultCamelContext();
        ContextHealthCheck check = new ContextHealthCheck();
        check.setCamelContext(context);

        try {
            HealthCheck.Result beforeStart = check.call();
            assertThat(beforeStart.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(beforeStart.getDetails())
                    .containsEntry(HealthCheck.CHECK_ID, "context")
                    .containsEntry(HealthCheck.CHECK_GROUP, "camel")
                    .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.ALL)
                    .containsKey("context.name")
                    .containsKey("context.version")
                    .containsKey("context.status")
                    .containsKey("context.phase");
            assertThat(beforeStart.getMessage()).isPresent();

            check.setEnabled(false);

            HealthCheck.Result disabled = check.call(Map.of(HealthCheck.CHECK_KIND, HealthCheck.Kind.LIVENESS));
            assertThat(disabled.getState()).isEqualTo(HealthCheck.State.UNKNOWN);
            assertThat(disabled.getDetails())
                    .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.LIVENESS)
                    .containsEntry(HealthCheck.CHECK_ENABLED, false);
            assertThat(disabled.getMessage()).contains("Disabled");
        } finally {
            context.stop();
        }

        check.setEnabled(true);
        HealthCheck.Result afterStop = check.call();
        assertThat(afterStop.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(afterStop.getDetails())
                .containsEntry(HealthCheck.FAILURE_COUNT, 2)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 0)
                .containsKey(HealthCheck.FAILURE_TIME)
                .containsKey(HealthCheck.FAILURE_START_TIME);
    }

    @Test
    void abstractHealthCheckTracksMetadataDisabledStateAndCustomResultStrategy() {
        SimpleRegistry simpleRegistry = new SimpleRegistry();
        DefaultCamelContext context = new DefaultCamelContext(simpleRegistry);
        AtomicInteger strategyInvocations = new AtomicInteger();
        HealthCheckResultStrategy strategy = (check, options, builder) -> {
            strategyInvocations.incrementAndGet();
            builder.detail("strategy.option", options.get("option"));
            if (Boolean.TRUE.equals(options.get("forceDown"))) {
                builder.message("forced by strategy").down();
            }
        };
        simpleRegistry.bind("healthStrategy", HealthCheckResultStrategy.class, strategy);

        RecordingHealthCheck check = new RecordingHealthCheck("custom", "tracked", true, true);
        check.setCamelContext(context);
        check.setNextState(HealthCheck.State.UP);

        HealthCheck.Result first = check.call(Map.of("option", "alpha"));
        assertThat(first.getState()).isEqualTo(HealthCheck.State.UP);
        assertThat(first.getDetails())
                .containsEntry(HealthCheck.CHECK_ID, "tracked")
                .containsEntry(HealthCheck.CHECK_GROUP, "custom")
                .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.ALL)
                .containsEntry("strategy.option", "alpha")
                .containsEntry(HealthCheck.INVOCATION_COUNT, 1)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 1)
                .containsEntry(HealthCheck.FAILURE_COUNT, 0);
        assertThat(strategyInvocations.get()).isEqualTo(1);

        HealthCheck.Result forcedDown = check.call(Map.of("option", "beta", "forceDown", true));
        assertThat(forcedDown.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(forcedDown.getMessage()).contains("forced by strategy");
        assertThat(forcedDown.getDetails())
                .containsEntry("strategy.option", "beta")
                .containsEntry(HealthCheck.INVOCATION_COUNT, 2)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 2)
                .containsEntry(HealthCheck.FAILURE_COUNT, 0);
        assertThat(strategyInvocations.get()).isEqualTo(2);

        check.setNextState(HealthCheck.State.DOWN);
        HealthCheck.Result naturalDown = check.call(Map.of("option", "gamma"));
        assertThat(naturalDown.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(naturalDown.getDetails())
                .containsEntry(HealthCheck.INVOCATION_COUNT, 3)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 0)
                .containsEntry(HealthCheck.FAILURE_COUNT, 1)
                .containsKey(HealthCheck.FAILURE_TIME)
                .containsKey(HealthCheck.FAILURE_START_TIME);
        assertThat(strategyInvocations.get()).isEqualTo(3);

        check.setEnabled(false);
        HealthCheck.Result disabled = check.call();
        assertThat(disabled.getState()).isEqualTo(HealthCheck.State.UNKNOWN);
        assertThat(disabled.getMessage()).contains("Disabled");
        assertThat(disabled.getDetails()).containsEntry(HealthCheck.CHECK_ENABLED, false);
        assertThat(strategyInvocations.get()).isEqualTo(4);
        assertThat(check.getMetaData()).containsEntry(HealthCheck.CHECK_ID, "tracked");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> check.getMetaData().put("other", "value"));
    }

    @Test
    void defaultRegistryRegistersResolvesFiltersAndStreamsHealthChecks() {
        DefaultCamelContext context = new DefaultCamelContext();
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry(context);
        RecordingHealthCheck readiness = new RecordingHealthCheck("custom", "ready", true, false);
        RecordingHealthCheck liveness = new RecordingHealthCheck("custom", "live", false, true);
        ProducersHealthCheckRepository repository = new ProducersHealthCheckRepository();
        repository.setEnabled(true);
        RecordingHealthCheck producer = new RecordingHealthCheck("producer", "producer:orders", true, false);
        repository.addHealthCheck(producer);

        assertThat(registry.register(readiness)).isTrue();
        assertThat(registry.register(readiness)).isFalse();
        assertThat(registry.register(liveness)).isTrue();
        assertThat(registry.register(repository)).isTrue();
        assertThat(registry.getCheckIDs()).contains("ready", "live", "producer:orders");
        assertThat(registry.getCheck("ready")).containsSame(readiness);
        assertThat(registry.getRepository(ProducersHealthCheckRepository.REPOSITORY_ID)).containsSame(repository);
        assertThat(registry.getRepository("producers")).containsSame(repository);
        assertThat(registry.resolveById("ready")).isSameAs(readiness);
        assertThat(registry.resolveById(ProducersHealthCheckRepository.REPOSITORY_ID)).isSameAs(repository);

        registry.setExcludePattern("ready,orders");
        assertThat(registry.isExcluded(readiness)).isTrue();
        assertThat(registry.isExcluded(producer)).isTrue();
        assertThat(registry.isExcluded(liveness)).isFalse();

        registry.setEnabled(false);
        assertThat(registry.stream()).isEmpty();
        registry.setEnabled(true);
        assertThat(registry.unregister(repository)).isTrue();
        assertThat(registry.getCheckIDs()).containsExactlyInAnyOrder("ready", "live");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.register("not a health object"));
    }

    @Test
    void healthCheckHelperInvokesRegistryWithKindsExposureAndExclusions() {
        DefaultCamelContext context = new DefaultCamelContext();
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry(context);
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);

        RecordingHealthCheck readinessUp = new RecordingHealthCheck("custom", "readiness-up", true, false);
        RecordingHealthCheck livenessDown = new RecordingHealthCheck("custom", "liveness-down", false, true);
        livenessDown.setNextState(HealthCheck.State.DOWN);
        registry.register(readinessUp);
        registry.register(livenessDown);

        Collection<HealthCheck.Result> readinessResults = HealthCheckHelper.invokeReadiness(context);
        assertThat(readinessResults)
                .singleElement()
                .extracting(result -> result.getCheck().getId(), HealthCheck.Result::getState)
                .containsExactly("readiness-up", HealthCheck.State.UP);

        Collection<HealthCheck.Result> livenessResults = HealthCheckHelper.invokeLiveness(context);
        assertThat(livenessResults)
                .singleElement()
                .extracting(result -> result.getCheck().getId(), HealthCheck.Result::getState)
                .containsExactly("liveness-down", HealthCheck.State.DOWN);
        assertThat(HealthCheckHelper.isResultsUp(livenessResults, true)).isFalse();
        assertThat(HealthCheckHelper.isResultsUp(readinessResults, false)).isTrue();

        registry.setExposureLevel("oneline");
        Collection<HealthCheck.Result> onelineResults = HealthCheckHelper.invoke(context);
        assertThat(onelineResults)
                .singleElement()
                .extracting(HealthCheck.Result::getState)
                .isEqualTo(HealthCheck.State.DOWN);

        registry.setExcludePattern("liveness-down");
        Collection<HealthCheck.Result> filteredResults = HealthCheckHelper.invoke(context);
        assertThat(filteredResults)
                .singleElement()
                .extracting(result -> result.getCheck().getId())
                .isEqualTo("readiness-up");

        Optional<HealthCheck.Result> invokedById = HealthCheckHelper.invoke(
                context,
                "readiness-up",
                Map.of("payload", "value"));
        assertThat(invokedById).isPresent();
        assertThat(invokedById.orElseThrow().getDetails()).containsEntry("payload", "value");
        assertThat(HealthCheckHelper.getHealthCheck(context, "readiness-up", RecordingHealthCheck.class))
                .isSameAs(readinessUp);
        assertThat(HealthCheckHelper.getHealthCheckRepository(context, "missing")).isNull();
        assertThat(HealthCheckHelper.isReservedKey(HealthCheck.SUCCESS_TIME)).isTrue();
        assertThat(HealthCheckHelper.isReservedKey("application.detail")).isFalse();
    }

    @Test
    void loaderAndBuiltInRouteControllerHealthCheckUseCamelHealthServices() {
        DefaultCamelContext context = new DefaultCamelContext();
        try {
            DefaultHealthChecksLoader loader = new DefaultHealthChecksLoader(context);
            Collection<HealthCheck> loadedChecks = loader.loadHealthChecks();
            assertThat(loadedChecks).extracting(HealthCheck::getId).contains("route-controller");
            assertThat(loadedChecks).allSatisfy(check -> assertThat(check.getGroup()).isEqualTo("camel"));

            RouteControllerHealthCheck routeController = loadedChecks.stream()
                    .filter(RouteControllerHealthCheck.class::isInstance)
                    .map(RouteControllerHealthCheck.class::cast)
                    .findFirst()
                    .orElseGet(RouteControllerHealthCheck::new);
            routeController.setCamelContext(context);

            HealthCheck.Result result = routeController.call();
            assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
            assertThat(result.getDetails())
                    .containsEntry(HealthCheck.CHECK_ID, "route-controller")
                    .containsEntry(HealthCheck.CHECK_GROUP, "camel")
                    .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS)
                    .containsEntry(HealthCheck.INVOCATION_COUNT, 1)
                    .containsEntry(HealthCheck.SUCCESS_COUNT, 1);
            assertThat(result.getError()).isEmpty();
        } finally {
            context.stop();
        }
    }

    @Test
    void writableProducerRepositoryAddsRemovesAndFindsChecks() {
        ProducersHealthCheckRepository repository = new ProducersHealthCheckRepository();
        repository.setCamelContext(new DefaultCamelContext());
        repository.setEnabled(true);
        RecordingHealthCheck first = new RecordingHealthCheck("producer", "producer:first", true, false);
        RecordingHealthCheck second = new RecordingHealthCheck("producer", "producer:second", true, false);

        repository.addHealthCheck(first);
        repository.addHealthCheck(second);

        assertThat(repository.getId()).isEqualTo(ProducersHealthCheckRepository.REPOSITORY_ID);
        assertThat(repository.getCheck("producer:first")).containsSame(first);
        assertThat(repository.stream().map(HealthCheck::getId).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("producer:first", "producer:second");

        repository.setEnabled(false);
        assertThat(repository.stream()).isEmpty();
        repository.setEnabled(true);
        repository.removeHealthCheck(first);
        assertThat(repository.getCheck("producer:first")).isEmpty();
        assertThat(repository.stream()).singleElement().isSameAs(second);
    }

    private static final class RecordingHealthCheck extends AbstractHealthCheck {
        private final boolean readiness;
        private final boolean liveness;
        private HealthCheck.State nextState = HealthCheck.State.UP;

        private RecordingHealthCheck(String group, String id, boolean readiness, boolean liveness) {
            super(group, id);
            this.readiness = readiness;
            this.liveness = liveness;
        }

        private void setNextState(HealthCheck.State nextState) {
            this.nextState = nextState;
        }

        @Override
        public boolean isReadiness() {
            return readiness;
        }

        @Override
        public boolean isLiveness() {
            return liveness;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.details(options);
            if (nextState == HealthCheck.State.UP) {
                builder.up();
            } else if (nextState == HealthCheck.State.DOWN) {
                builder.down().message("recorded down");
            } else {
                builder.unknown();
            }
        }
    }
}
