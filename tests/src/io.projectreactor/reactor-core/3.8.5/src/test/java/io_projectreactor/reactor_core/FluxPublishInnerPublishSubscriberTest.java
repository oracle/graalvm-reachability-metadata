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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxPublishInnerPublishSubscriberTest {

    @Test
    void sharesOneSourceSubscriptionBetweenConsumers() throws Exception {
        AtomicInteger subscriptions = new AtomicInteger();
        ConnectableFlux<String> published = Flux.just("first", "second")
                .doOnSubscribe(subscription -> subscriptions.incrementAndGet())
                .hide()
                .publish(8);
        CompletableFuture<List<String>> first = published.collectList().toFuture();
        CompletableFuture<List<String>> second = published.collectList().toFuture();

        assertThat(subscriptions).hasValue(0);
        Disposable connection = published.connect();
        try {
            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly("first", "second");
            assertThat(second.get(10, TimeUnit.SECONDS)).containsExactly("first", "second");
            assertThat(subscriptions).hasValue(1);
        } finally {
            first.cancel(true);
            second.cancel(true);
            connection.dispose();
        }
    }
}
