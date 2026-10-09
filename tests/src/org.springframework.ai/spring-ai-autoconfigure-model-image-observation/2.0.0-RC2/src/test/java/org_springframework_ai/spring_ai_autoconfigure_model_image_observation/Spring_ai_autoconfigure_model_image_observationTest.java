/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_image_observation;

import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelPromptContentObservationHandler;
import org.springframework.ai.model.image.observation.autoconfigure.ImageObservationAutoConfiguration;
import org.springframework.ai.model.image.observation.autoconfigure.ImageObservationProperties;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_image_observationTest {

    @Test
    void autoConfigurationIsRegisteredForSpringBootDiscovery() {
        ClassLoader classLoader = Spring_ai_autoconfigure_model_image_observationTest.class
                .getClassLoader();
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();

        assertThat(candidates).contains(ImageObservationAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationBindsPropertiesAndIsDisabledByDefault() {
        try (AnnotationConfigApplicationContext context = contextWithTracer(false)) {
            ImageObservationProperties properties = context.getBean(ImageObservationProperties.class);

            assertThat(properties.isLogPrompt()).isFalse();
            assertThat(context.getBeansOfType(ImageModelPromptContentObservationHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(TracingAwareLoggingObservationHandler.class)).isEmpty();
        }
    }

    @Test
    void autoConfigurationCreatesTracingAwarePromptHandlerWhenLoggingIsEnabled() {
        try (AnnotationConfigApplicationContext context = contextWithTracer(true)) {
            ImageObservationProperties properties = context.getBean(ImageObservationProperties.class);
            Map<String, TracingAwareLoggingObservationHandler> handlers = context
                    .getBeansOfType(TracingAwareLoggingObservationHandler.class);
            TracingAwareLoggingObservationHandler handler = handlers.get("imageModelPromptContentObservationHandler");

            assertThat(properties.isLogPrompt()).isTrue();
            assertThat(handlers).containsOnlyKeys("imageModelPromptContentObservationHandler");
            assertThat(handler.supportsContext(new Observation.Context())).isFalse();
            assertThat(handler.supportsContext(imageObservationContext())).isTrue();
        }
    }

    @Test
    void autoConfigurationBacksOffWhenUserSuppliesPromptHandler() {
        ImageModelPromptContentObservationHandler userHandler = new ImageModelPromptContentObservationHandler();
        try (AnnotationConfigApplicationContext context = contextWithTracer(true, userHandler)) {
            Map<String, ImageModelPromptContentObservationHandler> handlers = context
                    .getBeansOfType(ImageModelPromptContentObservationHandler.class);

            assertThat(handlers).containsOnlyKeys("userImageModelPromptContentObservationHandler");
            assertThat(handlers).containsEntry("userImageModelPromptContentObservationHandler", userHandler);
            assertThat(context.getBeansOfType(TracingAwareLoggingObservationHandler.class)).isEmpty();
        }
    }

    @Test
    void promptHandlerRecognizesImageObservationContexts() {
        ImageModelPromptContentObservationHandler handler = new ImageModelPromptContentObservationHandler();
        ImageModelObservationContext observationContext = imageObservationContext();

        assertThat(handler.supportsContext(new Observation.Context())).isFalse();
        assertThat(handler.supportsContext(observationContext)).isTrue();

        handler.onStop(observationContext);
    }

    private static ImageModelObservationContext imageObservationContext() {
        ImagePrompt prompt = new ImagePrompt(List.of(new ImageMessage("A mountain at sunrise")),
                ImageOptionsBuilder.builder().model("test-image-model").responseFormat("b64_json")
                        .width(512).height(512).style("vivid").build());
        ImageModelObservationContext context = ImageModelObservationContext.builder()
                .imagePrompt(prompt)
                .provider("test-provider")
                .build();

        assertThat(context.getOperationType()).isEqualTo(AiOperationType.IMAGE.value());
        return context;
    }

    private static AnnotationConfigApplicationContext contextWithTracer(boolean logPrompt) {
        return contextWithTracer(logPrompt, null);
    }

    private static AnnotationConfigApplicationContext contextWithTracer(boolean logPrompt,
            ImageModelPromptContentObservationHandler userHandler) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-properties",
                Map.of(ImageObservationProperties.CONFIG_PREFIX + ".log-prompt", Boolean.toString(logPrompt))));
        context.getBeanFactory().registerSingleton("tracer", Tracer.NOOP);
        if (userHandler != null) {
            context.getBeanFactory().registerSingleton("userImageModelPromptContentObservationHandler", userHandler);
        }
        context.register(ImageObservationAutoConfiguration.class);
        context.refresh();
        return context;
    }

}
