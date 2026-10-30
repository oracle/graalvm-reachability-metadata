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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublishersTest {
    @Test
    void discoversAndClassifiesReactiveTypes() {
        assertTrue(Publishers.getKnownReactiveTypes().contains(Flux.class));
        assertTrue(Publishers.getKnownSingleTypes().contains(Mono.class));
        assertTrue(Publishers.isSingle(Publishers.just("value").getClass()));
        assertTrue(Publishers.isSingle(Publishers.just(new IllegalArgumentException("invalid")).getClass()));
        assertTrue(Publishers.isSingle(Publishers.fromCompletableFuture(
                CompletableFuture.completedFuture("value")).getClass()));
        assertTrue(Publishers.isConvertibleToPublisher(Flux.class));
        assertTrue(Publishers.isConvertibleToPublisher(Publishers.just("value")));
        assertFalse(Publishers.isSingle(Flux.class));
        assertFalse(Publishers.isConvertibleToPublisher(String.class));
        assertFalse(Publishers.isConvertibleToPublisher("text"));
        assertFalse(Publishers.isConvertibleToPublisher((Object) null));
    }

    @Test
    void mapsAndObservesValues() {
        AtomicReference<String> observed = new AtomicReference<>();
        Publisher<Integer> mapped = Publishers.map(
                Publishers.then(Publishers.just("orchard"), observed::set), String::length);

        assertEquals(List.of(7), Flux.from(mapped).collectList().block(Duration.ofSeconds(10)));
        assertEquals("orchard", observed.get());
    }

    @Test
    void mapsValuesOrSuppliesAnEmptyResult() {
        Publishers.MapOrSupplyEmpty<String, Integer> mapper = new Publishers.MapOrSupplyEmpty<>() {
            @Override
            public Integer map(String value) {
                return value.length();
            }

            @Override
            public Integer supplyEmpty() {
                return 0;
            }
        };

        assertEquals(List.of(5), Flux.from(Publishers.mapOrSupplyEmpty(Publishers.just("peach"), mapper))
                .collectList().block(Duration.ofSeconds(10)));
        assertEquals(List.of(0), Flux.from(Publishers.mapOrSupplyEmpty(Publishers.empty(), mapper))
                .collectList().block(Duration.ofSeconds(10)));
    }

    @Test
    void waitsForCompletionAction() throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        CompletableFuture<List<String>> result = Flux.from(
                Publishers.onComplete(Publishers.just("ready"), () -> completion)).collectList().toFuture();

        assertFalse(result.isDone());
        completion.complete(null);
        assertEquals(List.of("ready"), result.get(10, TimeUnit.SECONDS));
    }

    @Test
    void buffersSourceBeforeDownstreamSubscribes() {
        AtomicInteger produced = new AtomicInteger();
        Publisher<Integer> buffered = Publishers.bufferNow(Flux.just(2, 4, 6)
                .doOnNext(value -> produced.incrementAndGet()));

        assertEquals(3, produced.get());
        assertEquals(List.of(2, 4, 6), Flux.from(buffered).collectList().block(Duration.ofSeconds(10)));
    }

    @Test
    void adaptsFutureAndPreservesExistingPublisher() {
        Publisher<String> original = Publishers.just("direct");
        assertSame(original, Publishers.convertToPublisher(ConversionService.SHARED, original));
        assertSame(original, Publishers.convertPublisher(ConversionService.SHARED, original, Publisher.class));

        Publisher<String> converted = Publishers.convertToPublisher(ConversionService.SHARED,
                CompletableFuture.completedFuture("future"));
        assertEquals("future", Mono.from(converted).block(Duration.ofSeconds(10)));
    }
}
