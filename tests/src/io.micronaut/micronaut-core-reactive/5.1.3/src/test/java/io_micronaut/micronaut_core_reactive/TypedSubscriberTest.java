/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.type.Argument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

public class TypedSubscriberTest {
    @Test
    void receivesValuesWhileRetainingItsDeclaredType() {
        RecordingSubscriber subscriber = new RecordingSubscriber();

        Publishers.just("reactive-value").subscribe(subscriber);

        assertThat(subscriber.getTypeArgument().getType()).isEqualTo(String.class);
        assertThat(subscriber.values).containsExactly("reactive-value");
        assertThat(subscriber.error).isNull();
        assertThat(subscriber.complete).isTrue();
    }

    private static final class RecordingSubscriber extends TypedSubscriber<String> {
        private final List<String> values = new ArrayList<>();
        private Throwable error;
        private boolean complete;

        private RecordingSubscriber() {
            super(Argument.of(String.class));
        }

        @Override
        protected void doOnSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        protected void doOnNext(String value) {
            values.add(value);
        }

        @Override
        protected void doOnError(Throwable throwable) {
            error = throwable;
        }

        @Override
        protected void doOnComplete() {
            complete = true;
        }
    }
}
