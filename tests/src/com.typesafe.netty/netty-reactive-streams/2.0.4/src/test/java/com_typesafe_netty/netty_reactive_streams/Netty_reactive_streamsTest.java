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
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_reactive_streamsTest {
    @Test
    void cancelledSubscriberImmediatelyCancelsAndValidatesRequiredSignals() {
        CancelledSubscriber<String> subscriber = new CancelledSubscriber<>();
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext("ignored");
        subscriber.onError(new IllegalStateException("ignored"));
        subscriber.onComplete();

        assertThat(subscription.isCancelled()).isTrue();
        assertThat(subscription.requested()).isZero();
        assertThatThrownBy(() -> subscriber.onSubscribe(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null subscription");
        assertThatThrownBy(() -> subscriber.onError(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null error published");
    }

    @Test
    void handlerPublisherPublishesMatchingInboundMessagesWithBackpressureAndForwardsOthers() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.request(2);
            runPendingTasks(channel);

            channel.writeInbound("one", 42, "two", "three");

            Object forwardedInbound = channel.readInbound();
            Object noMoreInbound = channel.readInbound();

            assertThat(subscriber.values()).containsExactly("one", "two");
            assertThat(forwardedInbound).isEqualTo(42);
            assertThat(noMoreInbound).isNull();
            assertThat(subscriber.completions()).isZero();

            subscriber.request(1);
            runPendingTasks(channel);
            channel.close();
            runPendingTasks(channel);

            assertThat(subscriber.values()).containsExactly("one", "two", "three");
            assertThat(subscriber.errors()).isEmpty();
            assertThat(subscriber.completions()).isOne();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherCancellationClosesChannelWithoutTerminalSignal() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.cancel();
            runPendingTasks(channel);

            assertThat(channel.isOpen()).isFalse();
            assertThat(subscriber.values()).isEmpty();
            assertThat(subscriber.errors()).isEmpty();
            assertThat(subscriber.completions()).isZero();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherRejectsInvalidDemandAndAdditionalSubscribers() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RecordingSubscriber<String> firstSubscriber = new RecordingSubscriber<>();
            RecordingSubscriber<String> secondSubscriber = new RecordingSubscriber<>();

            publisher.subscribe(firstSubscriber);
            runPendingTasks(channel);
            publisher.subscribe(secondSubscriber);
            firstSubscriber.request(0);
            runPendingTasks(channel);

            assertThat(secondSubscriber.subscription()).isNotNull();
            assertThat(secondSubscriber.errors()).hasSize(1);
            assertThat(secondSubscriber.errors().get(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("This publisher only supports one subscriber");
            assertThat(firstSubscriber.errors()).hasSize(1);
            assertThat(firstSubscriber.errors().get(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Request for 0 or negative elements");
            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherDrainsMessagesBufferedBeforeDemandAndThenCompletes() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            channel.writeInbound("first", "second");
            channel.close();
            runPendingTasks(channel);

            assertThat(subscriber.values()).isEmpty();
            assertThat(subscriber.completions()).isZero();

            subscriber.request(3);
            runPendingTasks(channel);

            assertThat(subscriber.values()).containsExactly("first", "second");
            assertThat(subscriber.errors()).isEmpty();
            assertThat(subscriber.completions()).isOne();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherDeliversStoredChannelFailureToLateSubscriber() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RuntimeException failure = new RuntimeException("channel failed before subscription");
            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();

            channel.pipeline().fireExceptionCaught(failure);
            publisher.subscribe(subscriber);
            runPendingTasks(channel);

            assertThat(subscriber.subscription()).isNotNull();
            assertThat(subscriber.values()).isEmpty();
            assertThat(subscriber.errors()).containsExactly(failure);
            assertThat(subscriber.completions()).isZero();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberRequestsDemandAndWritesOutboundMessages() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop(), 1, 3);
            channel.pipeline().addLast(subscriber);
            RecordingSubscription upstream = new RecordingSubscription();

            subscriber.onSubscribe(upstream);
            runPendingTasks(channel);
            subscriber.onNext("alpha");
            subscriber.onNext("beta");
            runPendingTasks(channel);

            Object firstOutbound = channel.readOutbound();
            Object secondOutbound = channel.readOutbound();
            Object noMoreOutbound = channel.readOutbound();

            assertThat(upstream.requested()).isEqualTo(5);
            assertThat(firstOutbound).isEqualTo("alpha");
            assertThat(secondOutbound).isEqualTo("beta");
            assertThat(noMoreOutbound).isNull();
            assertThat(upstream.isCancelled()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberInvokesCompletionHookWithoutCancellingUpstream() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            RecordingHandlerSubscriber<String> subscriber = new RecordingHandlerSubscriber<>(channel.eventLoop());
            channel.pipeline().addLast(subscriber);
            RecordingSubscription upstream = new RecordingSubscription();

            subscriber.onSubscribe(upstream);
            runPendingTasks(channel);
            subscriber.onComplete();
            runPendingTasks(channel);

            assertThat(upstream.requested()).isEqualTo(16);
            assertThat(subscriber.isComplete()).isTrue();
            assertThat(subscriber.error()).isNull();
            assertThat(upstream.isCancelled()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberCancelsDuplicateSubscriptionsAndCancelsUpstreamWhenChannelCloses() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
            channel.pipeline().addLast(subscriber);
            RecordingSubscription first = new RecordingSubscription();
            RecordingSubscription duplicate = new RecordingSubscription();

            subscriber.onSubscribe(first);
            runPendingTasks(channel);
            subscriber.onSubscribe(duplicate);
            channel.close();
            runPendingTasks(channel);

            assertThat(first.requested()).isEqualTo(16);
            assertThat(first.isCancelled()).isTrue();
            assertThat(duplicate.isCancelled()).isTrue();
            assertThatThrownBy(() -> subscriber.onSubscribe(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Null subscription");
            assertThatThrownBy(() -> subscriber.onError(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Null error published");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void runPendingTasks(EmbeddedChannel channel) {
        for (int i = 0; i < 3; i++) {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
        }
    }

    private static final class RecordingHandlerSubscriber<T> extends HandlerSubscriber<T> {
        private boolean complete;
        private Throwable error;

        RecordingHandlerSubscriber(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void complete() {
            complete = true;
        }

        @Override
        protected void error(Throwable error) {
            this.error = error;
        }

        boolean isComplete() {
            return complete;
        }

        Throwable error() {
            return error;
        }
    }

    private static final class RecordingSubscriber<T> implements Subscriber<T> {
        private final List<T> values = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();
        private Subscription subscription;
        private int completions;

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
            completions++;
        }

        void request(long demand) {
            assertThat(subscription).isNotNull();
            subscription.request(demand);
        }

        void cancel() {
            assertThat(subscription).isNotNull();
            subscription.cancel();
        }

        List<T> values() {
            return values;
        }

        List<Throwable> errors() {
            return errors;
        }

        Subscription subscription() {
            return subscription;
        }

        int completions() {
            return completions;
        }
    }

    private static final class RecordingSubscription implements Subscription {
        private final AtomicLong requested = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long demand) {
            requested.addAndGet(demand);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        long requested() {
            return requested.get();
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }
}
