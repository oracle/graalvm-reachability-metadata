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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_embedding_observationTest {

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
