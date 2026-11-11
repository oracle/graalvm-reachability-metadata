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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class MonoCallableInnerMonoCallableSubscriptionTest {

    @Test
    void invokesCallableOnlyOnceWhenDemandArrives() {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean complete = new AtomicBoolean();
        List<Integer> values = new ArrayList<>();
        BaseSubscriber<Integer> subscriber = new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // Demand is supplied explicitly after checking that the callable is lazy.
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
        try {
            Mono.fromCallable(calls::incrementAndGet).subscribe(subscriber);
            assertThat(calls).hasValue(0);
            assertThat(values).isEmpty();

            subscriber.request(1);
            subscriber.request(1);

            assertThat(values).containsExactly(1);
            assertThat(calls).hasValue(1);
            assertThat(complete).isTrue();
        } finally {
            subscriber.dispose();
        }
    }
}
