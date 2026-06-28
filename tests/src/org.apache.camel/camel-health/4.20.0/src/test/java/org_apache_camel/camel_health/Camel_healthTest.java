/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_health;

import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.health.HealthCheckResultStrategy;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.impl.health.ConsumersHealthCheckRepository;
import org.apache.camel.impl.health.ContextHealthCheck;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.impl.health.HealthCheckRegistryRepository;
import org.apache.camel.impl.health.ProducersHealthCheckRepository;
import org.apache.camel.impl.health.RouteControllerHealthCheck;
import org.apache.camel.impl.health.RoutesHealthCheckRepository;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Camel_healthTest {
    @Test
    void contextHealthCheckReportsUnknownWithoutCamelContextAndTracksInvocationCounters() {
        ContextHealthCheck healthCheck = new ContextHealthCheck();

        HealthCheck.Result readiness = healthCheck.callReadiness();
        HealthCheck.Result liveness = healthCheck.callLiveness();

        assertThat(healthCheck.getId()).isEqualTo("context");
        assertThat(healthCheck.getGroup()).isEqualTo("camel");
        assertThat(healthCheck.isLiveness()).isTrue();
        assertThat(healthCheck.getMetaData())
                .containsEntry(HealthCheck.CHECK_ID, "context")
                .containsEntry(HealthCheck.CHECK_GROUP, "camel");
        assertThat(readiness.getState()).isEqualTo(HealthCheck.State.UNKNOWN);
        assertThat(readiness.getCheck()).isSameAs(healthCheck);
        assertThat(readiness.getMessage()).isEmpty();
        assertThat(readiness.getError()).isEmpty();
        assertThat(readiness.getDetails())
                .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS)
                .containsEntry(HealthCheck.INVOCATION_COUNT, 1)
                .containsEntry(HealthCheck.FAILURE_COUNT, 0)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 0)
                .containsKey(HealthCheck.INVOCATION_TIME);
        assertThat(liveness.getState()).isEqualTo(HealthCheck.State.UNKNOWN);
        assertThat(liveness.getDetails())
                .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.LIVENESS)
                .containsEntry(HealthCheck.INVOCATION_COUNT, 2)
                .containsEntry(HealthCheck.SUCCESS_COUNT, 0);
    }

    @Test
    void contextHealthCheckReportsDownForStoppedCamelContext() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            ContextHealthCheck healthCheck = new ContextHealthCheck();
            healthCheck.setCamelContext(context);

            HealthCheck.Result result = healthCheck.callReadiness();

            assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(result.getMessage())
                    .hasValueSatisfying(message -> assertThat(message).contains("Camel Context"));
            assertThat(result.getDetails())
                    .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS)
                    .containsEntry("context.name", context.getName())
                    .containsKey("context.status")
                    .containsKey("context.phase")
                    .containsEntry(HealthCheck.INVOCATION_COUNT, 1)
                    .containsEntry(HealthCheck.FAILURE_COUNT, 1)
                    .containsEntry(HealthCheck.SUCCESS_COUNT, 0)
                    .containsKey(HealthCheck.FAILURE_TIME)
                    .containsKey(HealthCheck.FAILURE_START_TIME);
        }
    }

    @Test
    void abstractHealthCheckTracksStateTransitionsDisabledChecksAndCustomResultStrategy() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HealthCheckResultStrategy strategy = (check, options, builder) -> {
                builder.detail("strategy.check", check.getId());
                builder.detail("strategy.option", options.get("requested-by"));
                if (options.containsKey("requested-by")) {
                    builder.message("overridden by strategy");
                    builder.up();
                }
            };
            context.getRegistry().bind("healthResultStrategy", HealthCheckResultStrategy.class, strategy);

            FixedHealthCheck healthCheck = new FixedHealthCheck("custom", "database", HealthCheck.State.DOWN);
            healthCheck.setCamelContext(context);

            HealthCheck.Result result = healthCheck.call(Map.of("requested-by", "test"));

            assertThat(result.getState()).isEqualTo(HealthCheck.State.UP);
            assertThat(result.getMessage()).contains("overridden by strategy");
            assertThat(result.getDetails())
                    .containsEntry(HealthCheck.CHECK_ID, "database")
                    .containsEntry(HealthCheck.CHECK_GROUP, "custom")
                    .containsEntry("custom.state", HealthCheck.State.DOWN.name())
                    .containsEntry("strategy.check", "database")
                    .containsEntry("strategy.option", "test")
                    .containsEntry(HealthCheck.INVOCATION_COUNT, 1)
                    .containsEntry(HealthCheck.FAILURE_COUNT, 1)
                    .containsEntry(HealthCheck.SUCCESS_COUNT, 0);

            healthCheck.setEnabled(false);
            HealthCheck.Result disabled = healthCheck.callReadiness();

            assertThat(disabled.getState()).isEqualTo(HealthCheck.State.UNKNOWN);
            assertThat(disabled.getMessage()).contains("Disabled");
            assertThat(disabled.getDetails())
                    .containsEntry(HealthCheck.CHECK_ENABLED, false)
                    .containsEntry("strategy.check", "database");
        }
    }

    @Test
    void registryRegistersDirectChecksRepositoriesAndAppliesLookupAndExclusionRules() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry(context);
            FixedHealthCheck routeHealth = new FixedHealthCheck("camel", "route:orders", HealthCheck.State.UP);
            FixedHealthCheck producerHealth = new FixedHealthCheck("camel", "producer:billing", HealthCheck.State.UP);
            ProducersHealthCheckRepository producers = new ProducersHealthCheckRepository();
            producers.setEnabled(true);

            assertThat(registry.register(routeHealth)).isTrue();
            assertThat(registry.register(routeHealth)).isFalse();
            assertThat(registry.register(producers)).isTrue();
            producers.addHealthCheck(producerHealth);

            assertThat(routeHealth.getCamelContext()).isSameAs(context);
            assertThat(producers.getCamelContext()).isSameAs(context);
            assertThat(producerHealth.getCamelContext()).isSameAs(context);
            assertThat(registry.getCheck("route:orders")).contains(routeHealth);
            assertThat(registry.getCheck("orders")).contains(routeHealth);
            assertThat(registry.getRepository("producers")).contains(producers);
            assertThat(registry.resolveById("route-controller")).isInstanceOf(RouteControllerHealthCheck.class);
            assertThat(registry.resolveById("routes")).isInstanceOf(RoutesHealthCheckRepository.class);
            assertThat(registry.resolveById("consumers")).isInstanceOf(ConsumersHealthCheckRepository.class);
            assertThat(registry.resolveById("producers")).isInstanceOf(ProducersHealthCheckRepository.class);
            assertThat(registry.resolveById("producers")).isInstanceOf(HealthCheckRepository.class);
            assertThat(registry.getCheckIDs()).containsExactlyInAnyOrder("route:orders", "producer:billing");
            assertThat(registry.stream().toList()).containsExactlyInAnyOrder(routeHealth, producerHealth);

            registry.setExcludePattern("orders,payment*,producer:billing");
            assertThat(registry.isExcluded(routeHealth)).isTrue();
            FixedHealthCheck paymentConsumer = new FixedHealthCheck(
                    "camel", "consumer:paymentEvents", HealthCheck.State.UP);
            assertThat(registry.isExcluded(paymentConsumer)).isTrue();
            assertThat(registry.isExcluded(producerHealth)).isTrue();
            assertThat(registry.isExcluded(new FixedHealthCheck("camel", "context", HealthCheck.State.UP))).isFalse();

            registry.setEnabled(false);
            assertThat(registry.stream().toList()).isEmpty();

            registry.setEnabled(true);
            assertThat(registry.unregister(routeHealth)).isTrue();
            assertThat(registry.unregister(routeHealth)).isFalse();
            assertThat(registry.getCheckIDs()).containsExactly("producer:billing");
        }
    }

    @Test
    void producersRepositoryIsWritableContextAwareAndDisabledByDefault() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            ProducersHealthCheckRepository repository = new ProducersHealthCheckRepository();
            FixedHealthCheck producer = new FixedHealthCheck("camel", "producer:inventory", HealthCheck.State.UP);

            repository.setCamelContext(context);
            repository.addHealthCheck(producer);

            assertThat(repository.getId()).isEqualTo(ProducersHealthCheckRepository.REPOSITORY_ID);
            assertThat(repository.isEnabled()).isFalse();
            assertThat(repository.stream().toList()).isEmpty();
            assertThat(producer.getCamelContext()).isSameAs(context);

            repository.setEnabled(true);
            assertThat(repository.stream().toList()).containsExactly(producer);
            assertThat(repository.getCheck("producer:inventory")).contains(producer);

            repository.removeHealthCheck(producer);
            assertThat(repository.stream().toList()).isEmpty();
        }
    }

    @Test
    void consumersRepositoryCreatesChecksThatDelegateToConsumerHealthChecks() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            HealthCheck consumerHealth = new FixedHealthCheck("consumer", "orders-consumer", HealthCheck.State.DOWN);
            TestEndpoint endpoint = new TestEndpoint("test://orders?password=secret", consumerHealth);
            endpoint.setCamelContext(context);
            context.setRouteController(new StartedRouteController(context));
            DefaultRoute route = new DefaultRoute(context, null, "orders", null, null, endpoint, null);
            route.setProcessor(exchange -> exchange.getMessage().setBody("processed"));
            route.initializeServices();
            context.addRoute(route);

            ConsumersHealthCheckRepository repository = new ConsumersHealthCheckRepository();
            repository.setCamelContext(context);

            List<HealthCheck> checks = repository.stream().toList();

            assertThat(repository.getId()).isEqualTo("consumers");
            assertThat(repository.isEnabled()).isTrue();
            assertThat(checks).hasSize(1);

            HealthCheck check = checks.get(0);
            HealthCheck.Result result = check.callReadiness();
            assertThat(check.getId()).isEqualTo("consumer:orders");
            assertThat(check.getGroup()).isEqualTo("camel");
            assertThat(repository.stream().toList()).containsExactly(check);
            assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(result.getMessage()).contains("custom DOWN");
            assertThat(result.getDetails())
                    .containsEntry("route.id", "orders")
                    .containsKey("route.status")
                    .containsEntry("custom.state", HealthCheck.State.DOWN.name())
                    .containsEntry(HealthCheck.CHECK_KIND, HealthCheck.Kind.READINESS)
                    .containsEntry(HealthCheck.INVOCATION_COUNT, 1);
            assertThat((String) result.getDetails().get("endpoint.uri"))
                    .contains("test://orders")
                    .doesNotContain("secret");

            repository.setEnabled(false);
            assertThat(repository.stream().toList()).isEmpty();
        }
    }

    @Test
    void registryRepositoryStreamsHealthCheckBeansFromCamelRegistryAndHonorsEnabledFlag() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            FixedHealthCheck database = new FixedHealthCheck("external", "database", HealthCheck.State.UP);
            FixedHealthCheck cache = new FixedHealthCheck("external", "cache", HealthCheck.State.DOWN);
            context.getRegistry().bind("databaseHealth", HealthCheck.class, database);
            context.getRegistry().bind("cacheHealth", HealthCheck.class, cache);

            HealthCheckRegistryRepository repository = new HealthCheckRegistryRepository();
            repository.setCamelContext(context);

            assertThat(repository.getId()).isEqualTo("registry-health-check-repository");
            assertThat(repository.isEnabled()).isTrue();
            assertThat(repository.stream().toList()).containsExactlyInAnyOrder(database, cache);
            assertThat(repository.getCheck("database")).contains(database);
            assertThat(repository.getCheck("cache")).contains(cache);

            repository.setEnabled(false);
            assertThat(repository.stream().toList()).isEmpty();
            assertThat(repository.getCheck("database")).isEmpty();
        }
    }

    private static final class StartedRouteController extends DefaultRouteController {
        private StartedRouteController(DefaultCamelContext context) {
            super(context);
        }

        @Override
        public ServiceStatus getRouteStatus(String routeId) {
            return ServiceStatus.Started;
        }
    }

    private static final class TestEndpoint extends DefaultEndpoint {
        private final HealthCheck healthCheck;

        private TestEndpoint(String endpointUri, HealthCheck healthCheck) {
            this.healthCheck = healthCheck;
            setEndpointUriIfNotSpecified(endpointUri);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("Producer creation is not used by this test");
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            DefaultConsumer consumer = new DefaultConsumer(this, processor);
            consumer.setHealthCheck(healthCheck);
            return consumer;
        }
    }

    private static final class FixedHealthCheck extends AbstractHealthCheck {
        private final HealthCheck.State state;

        private FixedHealthCheck(String group, String id, HealthCheck.State state) {
            super(group, id);
            this.state = state;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.detail("custom.state", state.name());
            builder.message("custom " + state.name());
            builder.state(state);
        }
    }
}
