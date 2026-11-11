/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxPublishInnerPubSubInnerTest {

    @Test
    void publishedValuesWaitForDownstreamDemand() {
        List<Integer> values = new ArrayList<>();
        AtomicBoolean complete = new AtomicBoolean();
        BaseSubscriber<Integer> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(1);
            }

            @Override
            protected void hookOnNext(Integer value) {
                values.add(value);
            }

            @Override
            protected void hookOnComplete() {
                complete.set(true);
            }
        };
        ConnectableFlux<Integer> published = Flux.range(1, 3).hide().publish(8);
        published.subscribe(subscriber);
        Disposable connection = published.connect();
        try {
            assertThat(values).containsExactly(1);
            assertThat(complete).isFalse();

            subscriber.request(2);

            assertThat(values).containsExactly(1, 2, 3);
            assertThat(complete).isTrue();
        } finally {
            subscriber.dispose();
            connection.dispose();
        }
    }
}
