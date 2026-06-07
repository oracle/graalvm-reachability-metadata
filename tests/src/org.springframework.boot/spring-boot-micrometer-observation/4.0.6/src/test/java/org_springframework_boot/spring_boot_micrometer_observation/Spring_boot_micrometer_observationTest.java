/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationHandlerGroup;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer;
import org.springframework.boot.micrometer.observation.autoconfigure.ScheduledTasksObservationAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Spring_boot_micrometer_observationTest {

    @Test
    void observationPropertiesExposeDefaultsAndMutableNestedSettings() {
        ObservationProperties properties = new ObservationProperties();

        assertThat(properties.getHttp().getClient().getRequests().getName()).isEqualTo("http.client.requests");
        assertThat(properties.getHttp().getServer().getRequests().getName()).isEqualTo("http.server.requests");
        assertThat(properties.getEnable()).isEmpty();
        assertThat(properties.getKeyValues()).isEmpty();

        properties.getHttp().getClient().getRequests().setName("client.observation");
        properties.getHttp().getServer().getRequests().setName("server.observation");
        properties.setEnable(new LinkedHashMap<>(Map.of("all", false, "orders", true)));
        properties.setKeyValues(new LinkedHashMap<>(Map.of("application", "test-suite", "region", "eu")));

        assertThat(properties.getHttp().getClient().getRequests().getName()).isEqualTo("client.observation");
        assertThat(properties.getHttp().getServer().getRequests().getName()).isEqualTo("server.observation");
        assertThat(properties.getEnable()).containsEntry("all", false).containsEntry("orders", true);
        assertThat(properties.getKeyValues()).containsEntry("application", "test-suite").containsEntry("region", "eu");
    }

    @Test
    void autoConfigurationCreatesRegistryAndAppliesPropertiesHandlersAndCustomizers() {
        CustomizerConfiguration.reset();
        Map<String, Object> properties = Map.of(
                "management.observations.enable.all", "false",
                "management.observations.enable.orders", "true",
                "management.observations.key-values.application", "orders-service",
                "management.observations.key-values.region", "eu");

        try (AnnotationConfigApplicationContext context = contextWithProperties(properties,
                ObservationInfrastructureConfiguration.class, CustomizerConfiguration.class)) {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            RecordingObservationHandler handler = context.getBean(RecordingObservationHandler.class);

            Observation.start("orders.created", registry).stop();
            Observation disabledObservation = Observation.start("billing.created", registry);
            disabledObservation.stop();

            assertThat(context.getBean(ObservationProperties.class).getKeyValues())
                    .containsEntry("application", "orders-service")
                    .containsEntry("region", "eu");
            assertThat(CustomizerConfiguration.getCustomizationCount()).isEqualTo(1);
            assertThat(disabledObservation.isNoop()).isTrue();
            assertThat(handler.getStartedNames()).containsExactly("orders.created");
            assertThat(handler.getStoppedKeyValues()).singleElement().satisfies((keyValues) -> {
                assertThat(keyValues).containsEntry("application", "orders-service");
                assertThat(keyValues).containsEntry("region", "eu");
                assertThat(keyValues).containsEntry("customized", "true");
            });
        }
    }

    @Test
    void observationHandlerGroupRegistersMembersAsFirstMatchingCompositeHandler() {
        ObservationRegistry registry = ObservationRegistry.create();
        RecordingObservationHandler first = new RecordingObservationHandler();
        RecordingObservationHandler second = new RecordingObservationHandler();
        ObservationHandlerGroup group = ObservationHandlerGroup.of(RecordingObservationHandler.class);

        assertThat(group.handlerType()).isEqualTo(RecordingObservationHandler.class);
        assertThat(group.isMember(first)).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationHandlerGroup.of(null))
                .withMessageContaining("handlerType");

        group.registerMembers(registry.observationConfig(), List.of(first, second));
        Observation.start("grouped.observation", registry).stop();

        assertThat(first.getStoppedNames()).containsExactly("grouped.observation");
        assertThat(second.getStoppedNames()).isEmpty();
    }

    @Test
    void scheduledTasksAndValueExpressionResolverAreAutoConfigured() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of())) {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            SchedulingConfigurer schedulingConfigurer = context.getBean(SchedulingConfigurer.class);
            ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
            ValueExpressionResolver valueExpressionResolver = context.getBean(ValueExpressionResolver.class);

            schedulingConfigurer.configureTasks(registrar);

            assertThat(registrar.getObservationRegistry()).isSameAs(registry);
            assertThat(valueExpressionResolver.resolve("name", new NamedValue("spring"))).isEqualTo("spring");
        }
    }

    @Test
    void autoConfigurationRegistersObservationPredicatesGlobalConventionsAndFilters() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(Map.of(),
                ObservationInfrastructureConfiguration.class, AdditionalObservationComponentsConfiguration.class)) {
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            RecordingObservationHandler handler = context.getBean(RecordingObservationHandler.class);

            Observation skippedObservation = Observation.start("repository.skipped", registry);
            skippedObservation.stop();

            RepositoryObservationContext repositoryContext = new RepositoryObservationContext("orders", "tenant-a");
            Observation repositoryObservation = Observation.createNotStarted(
                    (ObservationConvention<RepositoryObservationContext>) null,
                    new DefaultRepositoryObservationConvention(), () -> repositoryContext, registry).start();
            repositoryObservation.stop();

            assertThat(skippedObservation.isNoop()).isTrue();
            assertThat(repositoryContext.getName()).isEqualTo("repository.operation");
            assertThat(repositoryContext.getContextualName()).isEqualTo("Repository orders");
            assertThat(repositoryContext.getLowCardinalityKeyValue("repository").getValue()).isEqualTo("orders");
            assertThat(repositoryContext.getLowCardinalityKeyValue("filtered").getValue()).isEqualTo("true");
            assertThat(repositoryContext.getHighCardinalityKeyValue("tenant").getValue()).isEqualTo("tenant-a");
            assertThat(handler.getStartedNames()).containsExactly("repository.operation");
            assertThat(handler.getStoppedKeyValues()).singleElement().satisfies((keyValues) -> {
                assertThat(keyValues).containsEntry("repository", "orders");
                assertThat(keyValues).containsEntry("filtered", "true");
            });
        }
    }

    private static AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties,
            Class<?>... configurationClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        context.register(ObservationAutoConfiguration.class, ScheduledTasksObservationAutoConfiguration.class);
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        try {
            context.refresh();
        } catch (RuntimeException ex) {
            context.close();
            throw ex;
        }
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    public static class ObservationInfrastructureConfiguration {

        @Bean
        RecordingObservationHandler recordingObservationHandler() {
            return new RecordingObservationHandler();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class AdditionalObservationComponentsConfiguration {

        @Bean
        ObservationPredicate skippedRepositoryObservationPredicate() {
            return (name, context) -> !"repository.skipped".equals(name);
        }

        @Bean
        GlobalObservationConvention<RepositoryObservationContext> repositoryObservationConvention() {
            return new RepositoryObservationConvention();
        }

        @Bean
        ObservationFilter filteredKeyValueObservationFilter() {
            return (context) -> context.addLowCardinalityKeyValue(KeyValue.of("filtered", "true"));
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class CustomizerConfiguration {

        private static final AtomicInteger CUSTOMIZATION_COUNT = new AtomicInteger();

        static void reset() {
            CUSTOMIZATION_COUNT.set(0);
        }

        static int getCustomizationCount() {
            return CUSTOMIZATION_COUNT.get();
        }

        @Bean
        ObservationRegistryCustomizer<ObservationRegistry> commonKeyValueCustomizer() {
            return new CommonKeyValueCustomizer();
        }

        private static final class CommonKeyValueCustomizer
                implements ObservationRegistryCustomizer<ObservationRegistry> {

            @Override
            public void customize(ObservationRegistry registry) {
                CUSTOMIZATION_COUNT.incrementAndGet();
                registry.observationConfig().observationFilter((context) -> context
                        .addLowCardinalityKeyValue(KeyValue.of("customized", "true")));
            }

        }

    }

    public static final class RepositoryObservationContext extends Context {

        private final String repository;

        private final String tenant;

        RepositoryObservationContext(String repository, String tenant) {
            this.repository = repository;
            this.tenant = tenant;
        }

    }

    private static final class DefaultRepositoryObservationConvention
            implements ObservationConvention<RepositoryObservationContext> {

        @Override
        public String getName() {
            return "repository.default";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(RepositoryObservationContext context) {
            return KeyValues.of("repository", "default");
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof RepositoryObservationContext;
        }

    }

    private static final class RepositoryObservationConvention
            implements GlobalObservationConvention<RepositoryObservationContext> {

        @Override
        public String getName() {
            return "repository.operation";
        }

        @Override
        public String getContextualName(RepositoryObservationContext context) {
            return "Repository " + context.repository;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(RepositoryObservationContext context) {
            return KeyValues.of("repository", context.repository);
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(RepositoryObservationContext context) {
            return KeyValues.of("tenant", context.tenant);
        }

        @Override
        public boolean supportsContext(Context context) {
            return context instanceof RepositoryObservationContext;
        }

    }

    public static final class RecordingObservationHandler implements ObservationHandler<Context> {

        private final List<String> startedNames = new ArrayList<>();

        private final List<String> stoppedNames = new ArrayList<>();

        private final List<Map<String, String>> stoppedKeyValues = new ArrayList<>();

        @Override
        public void onStart(Context context) {
            this.startedNames.add(context.getName());
        }

        @Override
        public void onStop(Context context) {
            this.stoppedNames.add(context.getName());
            Map<String, String> keyValues = new LinkedHashMap<>();
            context.getLowCardinalityKeyValues().forEach((keyValue) -> keyValues.put(keyValue.getKey(),
                    keyValue.getValue()));
            this.stoppedKeyValues.add(keyValues);
        }

        @Override
        public boolean supportsContext(Context context) {
            return true;
        }

        List<String> getStartedNames() {
            return this.startedNames;
        }

        List<String> getStoppedNames() {
            return this.stoppedNames;
        }

        List<Map<String, String>> getStoppedKeyValues() {
            return this.stoppedKeyValues;
        }

    }

    public static final class NamedValue {

        private final String name;

        NamedValue(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

    }

}
