/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactiveTypeConverterRegistrarTest {
    @Test
    void sharedConversionServiceDiscoversReactiveConverter() {
        ConversionService service = ConversionService.SHARED;
        assertTrue(service.canConvert(String.class, Publisher.class));
        Publisher<?> publisher = service.convertRequired("converted", Publisher.class);

        assertEquals(List.of("converted"), Flux.from(publisher).collectList().block(Duration.ofSeconds(10)));
    }

    @Test
    void publisherConversionPreservesTheStream() {
        Publisher<String> original = Publishers.just("unchanged");
        Publisher<?> converted = ConversionService.SHARED.convertRequired(original, Publisher.class);

        assertSame(original, converted);
        assertEquals(List.of("unchanged"), Flux.from(converted).collectList().block(Duration.ofSeconds(10)));
    }
}
