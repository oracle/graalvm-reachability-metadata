/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

public class SinkManyEmitterProcessorTest {

    @Test
    void buffersBeforeTheFirstSubscriberAndMulticastsLiveValues() throws Exception {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(8);
        assertThat(sink.tryEmitNext("queued")).isEqualTo(Sinks.EmitResult.OK);

        CompletableFuture<List<String>> first = sink.asFlux().collectList().toFuture();
        CompletableFuture<List<String>> second = sink.asFlux().collectList().toFuture();
        try {
            assertThat(sink.tryEmitNext("live")).isEqualTo(Sinks.EmitResult.OK);
            assertThat(sink.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);

            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly("queued", "live");
            assertThat(second.get(10, TimeUnit.SECONDS)).containsExactly("live");
        } finally {
            first.cancel(true);
            second.cancel(true);
        }
    }
}
