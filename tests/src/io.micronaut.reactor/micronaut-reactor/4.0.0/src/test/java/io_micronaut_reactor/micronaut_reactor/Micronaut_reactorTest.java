/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_reactor.micronaut_reactor;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.reactor.config.ReactorConfiguration;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Micronaut_reactorTest {
    @Test
    void convertsPublishersThroughMicronautConversionService() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConversionService conversionService = context.getBean(ConversionService.class);
            Publisher<Integer> source = Flux.just(2, 4, 6);

            Flux<Integer> convertedFlux = conversionService.convertRequired(source, Flux.class);
            Mono<Integer> convertedMono = conversionService.convertRequired(source, Mono.class);

            assertThat(convertedFlux.collectList().block(Duration.ofSeconds(10)))
                    .containsExactly(2, 4, 6);
            assertThat(convertedMono.block(Duration.ofSeconds(10))).isEqualTo(2);
        }
    }

    @Test
    void convertsMonoAndFluxValuesAcrossPublisherTypes() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ConversionService conversionService = context.getBean(ConversionService.class);
            Publisher<String> monoSource = Mono.just("reactive");
            Publisher<String> fluxSource = Flux.just("micronaut", "reactor");

            Flux<String> flux = conversionService.convertRequired(monoSource, Flux.class);
            Mono<String> mono = conversionService.convertRequired(fluxSource, Mono.class);

            assertThat(flux.collectList().block(Duration.ofSeconds(10)))
                    .containsExactly("reactive");
            assertThat(mono.block(Duration.ofSeconds(10))).isEqualTo("micronaut");
        }
    }

    @Test
    void startsConfiguredReactorIntegrationInApplicationContext() {
        try (ApplicationContext context = ApplicationContext.run(
                Map.of(
                        "reactor.enable-automatic-context-propagation", false,
                        "reactor.enable-schedule-hook-context-propagation", true))) {
            ReactorConfiguration configuration = context.getBean(ReactorConfiguration.class);

            assertThat(configuration.enableAutomaticContextPropagation()).isFalse();
            assertThat(configuration.enableScheduleHookContextPropagation()).isTrue();
            assertThat(context.getBeansOfType(ConversionService.class)).hasSize(1);
        }
    }
}
