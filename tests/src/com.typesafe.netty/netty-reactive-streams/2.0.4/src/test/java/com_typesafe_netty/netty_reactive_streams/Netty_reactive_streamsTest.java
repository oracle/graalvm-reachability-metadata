/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_netty.netty_reactive_streams;

import com.typesafe.netty.CancelledSubscriber;
import com.typesafe.netty.HandlerPublisher;
import com.typesafe.netty.HandlerSubscriber;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class Netty_reactive_streamsTest {
    @Test
    void cancelledSubscriberCancelsSubscriptionAndIgnoresSignals() {
        TestSubscription subscription = new TestSubscription();
        CancelledSubscriber<String> subscriber = new CancelledSubscriber<>();

        subscriber.onSubscribe(subscription);
        subscriber.onNext("ignored");
        subscriber.onComplete();
        subscriber.onError(new IllegalStateException("ignored"));

        assertThat(subscription.cancelled).isTrue();
        assertThatNullPointerException().isThrownBy(() -> subscriber.onSubscribe(null))
                .withMessage("Null subscription");
        assertThatNullPointerException().isThrownBy(() -> subscriber.onError(null))
                .withMessage("Null error published");
    }

    @Test
    void handlerPublisherBuffersInboundMessagesUntilSubscriberRequestsThem() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        channel.pipeline().addLast(publisher);
        publisher.subscribe(subscriber);
        channel.runPendingTasks();

        channel.writeInbound("first");
        channel.writeInbound("second");
        assertThat(subscriber.values).isEmpty();

        subscriber.subscription.request(1);
        channel.runPendingTasks();
        assertThat(subscriber.values).containsExactly("first");
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(1);
        channel.runPendingTasks();
        assertThat(subscriber.values).containsExactly("first", "second");
        assertThat(subscriber.errors).isEmpty();
    }

    @Test
    void handlerPublisherForwardsMessagesWithDifferentTypesDownstream() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        channel.pipeline().addLast(publisher);
        publisher.subscribe(subscriber);
        channel.runPendingTasks();
        subscriber.subscription.request(10);
        channel.runPendingTasks();

        channel.writeInbound("accepted");
        channel.writeInbound(42);

        assertThat(subscriber.values).containsExactly("accepted");
        assertThat(channel.<Integer>readInbound()).isEqualTo(42);
        Object remainingInbound = channel.readInbound();
        assertThat(remainingInbound).isNull();
    }

    @Test
    void handlerPublisherCompletesOnlyAfterBufferedMessagesAreDemanded() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        channel.pipeline().addLast(publisher);
        publisher.subscribe(subscriber);
        channel.runPendingTasks();

        channel.writeInbound("before-close");
        channel.pipeline().fireChannelInactive();
        assertThat(subscriber.values).isEmpty();
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(1);
        channel.runPendingTasks();
        assertThat(subscriber.values).containsExactly("before-close");
        assertThat(subscriber.completed).isFalse();

        subscriber.subscription.request(1);
        channel.runPendingTasks();
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.errors).isEmpty();
    }

    @Test
    void handlerPublisherDeliversExceptionThatArrivesBeforeSubscriber() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        RuntimeException failure = new RuntimeException("boom");
        channel.pipeline().addLast(publisher);

        channel.pipeline().fireExceptionCaught(failure);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        publisher.subscribe(subscriber);
        channel.runPendingTasks();

        assertThat(subscriber.subscription).isNotNull();
        assertThat(subscriber.errors).containsExactly(failure);
        assertThat(subscriber.values).isEmpty();
        assertThat(subscriber.completed).isFalse();
    }

    @Test
    void handlerPublisherRejectsInvalidDemandAndClosesChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        channel.pipeline().addLast(publisher);
        publisher.subscribe(subscriber);
        channel.runPendingTasks();

        subscriber.subscription.request(0);
        channel.runPendingTasks();

        assertThat(subscriber.errors).hasSize(1);
        assertThat(subscriber.errors.get(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request for 0 or negative elements in violation of Section 3.9 of the Reactive Streams specification");
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void handlerPublisherAllowsOnlyOneSubscriber() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
        TestSubscriber<String> first = new TestSubscriber<>();
        TestSubscriber<String> second = new TestSubscriber<>();
        channel.pipeline().addLast(publisher);

        publisher.subscribe(first);
        publisher.subscribe(second);
        channel.runPendingTasks();

        assertThat(first.subscription).isNotNull();
        assertThat(first.errors).isEmpty();
        assertThat(second.subscription).isNotNull();
        assertThat(second.errors).hasSize(1);
        assertThat(second.errors.get(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This publisher only supports one subscriber");
    }

    @Test
    void handlerPublisherReleasesBufferedMessagesWhenSubscriptionIsCancelled() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerPublisher<TestReferenceCountedMessage> publisher =
                new HandlerPublisher<>(channel.eventLoop(), TestReferenceCountedMessage.class);
        TestSubscriber<TestReferenceCountedMessage> subscriber = new TestSubscriber<>();
        TestReferenceCountedMessage bufferedMessage = new TestReferenceCountedMessage();
        channel.pipeline().addLast(publisher);
        publisher.subscribe(subscriber);
        channel.runPendingTasks();

        channel.writeInbound(bufferedMessage);
        assertThat(bufferedMessage.refCnt()).isEqualTo(1);
        assertThat(subscriber.values).isEmpty();

        subscriber.subscription.cancel();
        channel.runPendingTasks();

        assertThat(bufferedMessage.refCnt()).isZero();
        assertThat(subscriber.values).isEmpty();
        assertThat(subscriber.errors).isEmpty();
        assertThat(subscriber.completed).isFalse();
    }

    @Test
    void handlerSubscriberRequestsInitialDemandAndWritesReceivedElements() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop(), 1, 3);
        TestSubscription subscription = new TestSubscription();
        channel.pipeline().addLast(subscriber);

        subscriber.onSubscribe(subscription);
        channel.runPendingTasks();
        assertThat(subscription.requests).containsExactly(3L);

        subscriber.onNext("alpha");
        channel.runPendingTasks();
        assertThat(channel.<String>readOutbound()).isEqualTo("alpha");
        assertThat(subscription.requests).containsExactly(3L);

        subscriber.onNext("beta");
        channel.runPendingTasks();
        assertThat(channel.<String>readOutbound()).isEqualTo("beta");
        assertThat(subscription.requests).containsExactly(3L, 2L);
    }

    @Test
    void handlerSubscriberCancelsSecondSubscriptionImmediately() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
        TestSubscription first = new TestSubscription();
        TestSubscription second = new TestSubscription();
        channel.pipeline().addLast(subscriber);

        subscriber.onSubscribe(first);
        channel.runPendingTasks();
        subscriber.onSubscribe(second);

        assertThat(first.cancelled).isFalse();
        assertThat(second.cancelled).isTrue();
        assertThat(first.requests).containsExactly(16L);
    }

    @Test
    void handlerSubscriberCancelsSubscriptionWhenChannelBecomesInactive() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
        TestSubscription subscription = new TestSubscription();
        channel.pipeline().addLast(subscriber);
        subscriber.onSubscribe(subscription);
        channel.runPendingTasks();

        channel.pipeline().fireChannelInactive();

        assertThat(subscription.cancelled).isTrue();
    }

    @Test
    void handlerSubscriberClosesChannelOnError() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
        channel.pipeline().addLast(subscriber);

        subscriber.onError(new IllegalStateException("failed"));
        channel.runPendingTasks();

        assertThat(channel.isOpen()).isFalse();
        assertThatNullPointerException().isThrownBy(() -> subscriber.onError(null))
                .withMessage("Null error published");
    }

    @Test
    void handlerSubscriberClosesChannelAfterFinalWriteCompletes() {
        EmbeddedChannel channel = new EmbeddedChannel();
        HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
        TestSubscription subscription = new TestSubscription();
        channel.pipeline().addLast(subscriber);
        subscriber.onSubscribe(subscription);
        channel.runPendingTasks();

        subscriber.onNext("last");
        subscriber.onComplete();
        channel.runPendingTasks();

        assertThat(channel.<String>readOutbound()).isEqualTo("last");
        assertThat(channel.isOpen()).isFalse();
    }

    private static final class TestReferenceCountedMessage implements ReferenceCounted {
        private int references = 1;

        @Override
        public int refCnt() {
            return references;
        }

        @Override
        public TestReferenceCountedMessage retain() {
            references++;
            return this;
        }

        @Override
        public TestReferenceCountedMessage retain(int increment) {
            references += increment;
            return this;
        }

        @Override
        public TestReferenceCountedMessage touch() {
            return this;
        }

        @Override
        public TestReferenceCountedMessage touch(Object hint) {
            return this;
        }

        @Override
        public boolean release() {
            return release(1);
        }

        @Override
        public boolean release(int decrement) {
            references -= decrement;
            return references == 0;
        }
    }

    private static final class TestSubscriber<T> implements Subscriber<T> {
        private final List<T> values = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();
        private Subscription subscription;
        private boolean completed;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }

    private static final class TestSubscription implements Subscription {
        private final List<Long> requests = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void request(long elements) {
            requests.add(elements);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
