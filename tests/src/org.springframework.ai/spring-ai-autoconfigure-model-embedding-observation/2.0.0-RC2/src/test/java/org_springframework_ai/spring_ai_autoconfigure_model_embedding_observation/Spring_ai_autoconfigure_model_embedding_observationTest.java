/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_embedding_observation;

import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.DefaultEmbeddingOptions;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.EmbeddingModelMeterObservationHandler;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.model.embedding.observation.autoconfigure.EmbeddingObservationAutoConfiguration;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiTokenType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_embedding_observationTest {

    @Test
    void autoConfigurationIsRegisteredForSpringBootDiscovery() {
        ClassLoader classLoader = Spring_ai_autoconfigure_model_embedding_observationTest.class
                .getClassLoader();
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();

        assertThat(candidates).contains(EmbeddingObservationAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationCreatesMeterObservationHandlerWhenMeterRegistryIsPresent() {
        try (AnnotationConfigApplicationContext context = contextWithMeterRegistry()) {
            EmbeddingModelMeterObservationHandler handler = context.getBean(EmbeddingModelMeterObservationHandler.class);
            SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry.class);

            assertThat(handler.supportsContext(new Observation.Context())).isFalse();

            EmbeddingModelObservationContext observationContext = embeddingObservationContext();
            observationContext.setResponse(embeddingResponseWithUsage(7, 5));

            assertThat(handler.supportsContext(observationContext)).isTrue();

            handler.onStop(observationContext);

            assertTokenCounter(meterRegistry, AiTokenType.INPUT, 7.0);
            assertTokenCounter(meterRegistry, AiTokenType.OUTPUT, 5.0);
            assertTokenCounter(meterRegistry, AiTokenType.TOTAL, 12.0);
        }
    }

    @Test
    void autoConfigurationDoesNotCreateHandlerWithoutMeterRegistry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                EmbeddingObservationAutoConfiguration.class)) {
            assertThat(context.getBeansOfType(EmbeddingModelMeterObservationHandler.class)).isEmpty();
        }
    }

    @Test
    void autoConfigurationBacksOffWhenUserSuppliesHandler() {
        try (AnnotationConfigApplicationContext context = contextWithMeterRegistryAndUserHandler()) {
            Map<String, EmbeddingModelMeterObservationHandler> handlers = context.getBeansOfType(
                    EmbeddingModelMeterObservationHandler.class);

            assertThat(handlers).containsOnlyKeys("userEmbeddingModelMeterObservationHandler");
            assertThat(context.getBean(EmbeddingModelMeterObservationHandler.class))
                    .isSameAs(context.getBean("userEmbeddingModelMeterObservationHandler"));
        }
    }

    @Test
    void autoConfigurationUsesPrimaryMeterRegistryWhenMultipleRegistriesAreAvailable() {
        try (AnnotationConfigApplicationContext context = contextWithMultipleMeterRegistries()) {
            EmbeddingModelMeterObservationHandler handler = context.getBean(
                    EmbeddingModelMeterObservationHandler.class);
            SimpleMeterRegistry primaryMeterRegistry = context.getBean("primaryMeterRegistry",
                    SimpleMeterRegistry.class);
            SimpleMeterRegistry secondaryMeterRegistry = context.getBean("secondaryMeterRegistry",
                    SimpleMeterRegistry.class);

            EmbeddingModelObservationContext observationContext = embeddingObservationContext();
            observationContext.setResponse(embeddingResponseWithUsage(2, 3));

            handler.onStop(observationContext);

            assertTokenCounter(primaryMeterRegistry, AiTokenType.TOTAL, 5.0);
            assertNoTokenCounter(secondaryMeterRegistry, AiTokenType.TOTAL);
        }
    }

    private static EmbeddingModelObservationContext embeddingObservationContext() {
        EmbeddingRequest request = new EmbeddingRequest(List.of("Spring AI observations"),
                DefaultEmbeddingOptions.builder().model("test-embedding-model").dimensions(3).build());
        return EmbeddingModelObservationContext.builder()
                .embeddingRequest(request)
                .provider("test-provider")
                .build();
    }

    private static EmbeddingResponse embeddingResponseWithUsage(int promptTokens, int completionTokens) {
        Embedding embedding = new Embedding(new float[] { 0.1F, 0.2F, 0.3F }, 0);
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata("test-embedding-model",
                new TestUsage(promptTokens, completionTokens));
        return new EmbeddingResponse(List.of(embedding), metadata);
    }

    private static void assertTokenCounter(SimpleMeterRegistry meterRegistry, AiTokenType tokenType,
            double expectedCount) {
        Counter counter = meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value())
                .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), tokenType.value())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(expectedCount);
    }

    private static void assertNoTokenCounter(SimpleMeterRegistry meterRegistry, AiTokenType tokenType) {
        Counter counter = meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value())
                .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), tokenType.value())
                .counter();
        assertThat(counter).isNull();
    }

    private static AnnotationConfigApplicationContext contextWithMeterRegistry() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("simpleMeterRegistry", new SimpleMeterRegistry());
        context.register(EmbeddingObservationAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigApplicationContext contextWithMeterRegistryAndUserHandler() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmbeddingModelMeterObservationHandler handler = new EmbeddingModelMeterObservationHandler(meterRegistry);
        context.getBeanFactory().registerSingleton("simpleMeterRegistry", meterRegistry);
        context.getBeanFactory().registerSingleton("userEmbeddingModelMeterObservationHandler", handler);
        context.register(EmbeddingObservationAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigApplicationContext contextWithMultipleMeterRegistries() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MultipleMeterRegistryConfiguration.class);
        context.register(EmbeddingObservationAutoConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleMeterRegistryConfiguration {

        @Bean
        @Primary
        SimpleMeterRegistry primaryMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        SimpleMeterRegistry secondaryMeterRegistry() {
            return new SimpleMeterRegistry();
        }

    }

    static class TestUsage implements Usage {

        private final Integer promptTokens;

        private final Integer completionTokens;

        TestUsage(Integer promptTokens, Integer completionTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        @Override
        public Integer getPromptTokens() {
            return this.promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return this.completionTokens;
        }

        @Override
        public Object getNativeUsage() {
            return Map.of("promptTokens", this.promptTokens, "completionTokens", this.completionTokens);
        }

    }

}
