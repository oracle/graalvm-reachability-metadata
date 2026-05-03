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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_reactive_streamsTest {
    @Test
    void handlerPublisherPublishesMatchingInboundMessagesWithBackpressure() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            ForwardedMessageRecorder forwardedMessages = new ForwardedMessageRecorder();
            channel.pipeline().addLast(publisher);
            channel.pipeline().addLast(forwardedMessages);

            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.awaitSubscription();

            channel.writeInbound("buffered-one");
            assertThat(subscriber.items()).isEmpty();

            subscriber.request(1);
            runPendingTasks(channel);
            assertThat(subscriber.items()).containsExactly("buffered-one");

            channel.writeInbound(42);
            assertThat(forwardedMessages.messages()).containsExactly(42);

            subscriber.request(2);
            runPendingTasks(channel);
            channel.writeInbound("two");
            channel.writeInbound("three");

            assertThat(subscriber.items()).containsExactly("buffered-one", "two", "three");
            assertThat(subscriber.error()).isNull();

            channel.close().syncUninterruptibly();
            runPendingTasks(channel);
            subscriber.awaitTerminal();
            assertThat(subscriber.completed()).isTrue();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherRejectsAdditionalSubscribers() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);

            RecordingSubscriber<String> firstSubscriber = new RecordingSubscriber<>();
            publisher.subscribe(firstSubscriber);
            runPendingTasks(channel);
            firstSubscriber.awaitSubscription();

            RecordingSubscriber<String> secondSubscriber = new RecordingSubscriber<>();
            publisher.subscribe(secondSubscriber);
            secondSubscriber.awaitSubscription();
            secondSubscriber.awaitTerminal();

            assertThat(secondSubscriber.error())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("This publisher only supports one subscriber");
            assertThat(firstSubscriber.error()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherFailsInvalidDemandAndClosesChannel() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);

            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.awaitSubscription();

            subscriber.request(0);
            runPendingTasks(channel);
            subscriber.awaitTerminal();

            assertThat(subscriber.error())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Request for 0 or negative elements");
            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherClosesChannelAndDiscardsBufferedMessagesWhenSubscriberCancels() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);

            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.awaitSubscription();

            channel.writeInbound("queued");
            subscriber.cancel();
            runPendingTasks(channel);

            assertThat(channel.isOpen()).isFalse();
            assertThat(subscriber.items()).isEmpty();
            assertThat(subscriber.error()).isNull();
            assertThat(subscriber.completed()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherReplaysExceptionRaisedBeforeSubscriberArrives() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);
            RuntimeException failure = new RuntimeException("inbound failure");

            channel.pipeline().fireExceptionCaught(failure);
            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.awaitSubscription();
            subscriber.awaitTerminal();

            assertThat(subscriber.error()).isSameAs(failure);
            assertThat(subscriber.completed()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerPublisherEmitsBufferedMessagesBeforeCompletingClosedChannel() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerPublisher<String> publisher = new HandlerPublisher<>(channel.eventLoop(), String.class);
            channel.pipeline().addLast(publisher);

            RecordingSubscriber<String> subscriber = new RecordingSubscriber<>();
            publisher.subscribe(subscriber);
            runPendingTasks(channel);
            subscriber.awaitSubscription();

            channel.writeInbound("queued-before-close");
            channel.close().syncUninterruptibly();
            runPendingTasks(channel);

            assertThat(subscriber.items()).isEmpty();
            assertThat(subscriber.completed()).isFalse();
            assertThat(subscriber.error()).isNull();

            subscriber.request(2);
            runPendingTasks(channel);
            subscriber.awaitTerminal();

            assertThat(subscriber.items()).containsExactly("queued-before-close");
            assertThat(subscriber.completed()).isTrue();
            assertThat(subscriber.error()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberWritesPublisherElementsAndRefreshesDemand() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop(), 1, 3);
            channel.pipeline().addLast(subscriber);
            RecordingSubscription subscription = new RecordingSubscription();

            subscriber.onSubscribe(subscription);
            runPendingTasks(channel);
            assertThat(subscription.requests()).containsExactly(3L);

            subscriber.onNext("alpha");
            runPendingTasks(channel);
            assertThat(channel.<String>readOutbound()).isEqualTo("alpha");
            assertThat(subscription.requests()).containsExactly(3L);

            subscriber.onNext("bravo");
            runPendingTasks(channel);
            assertThat(channel.<String>readOutbound()).isEqualTo("bravo");
            assertThat(subscription.requests()).containsExactly(3L, 2L);

            subscriber.onComplete();
            runPendingTasks(channel);
            assertThat(channel.isOpen()).isFalse();
            assertThat(subscription.cancelled()).isTrue();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberCancelsDuplicateAndInactiveSubscriptions() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
            channel.pipeline().addLast(subscriber);
            RecordingSubscription firstSubscription = new RecordingSubscription();
            RecordingSubscription duplicateSubscription = new RecordingSubscription();

            subscriber.onSubscribe(firstSubscription);
            runPendingTasks(channel);
            subscriber.onSubscribe(duplicateSubscription);

            assertThat(duplicateSubscription.cancelled()).isTrue();
            assertThat(firstSubscription.cancelled()).isFalse();

            channel.close().syncUninterruptibly();
            runPendingTasks(channel);
            assertThat(firstSubscription.cancelled()).isTrue();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handlerSubscriberClosesChannelWhenPublisherSignalsError() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            HandlerSubscriber<String> subscriber = new HandlerSubscriber<>(channel.eventLoop());
            channel.pipeline().addLast(subscriber);
            subscriber.onSubscribe(new RecordingSubscription());
            runPendingTasks(channel);

            subscriber.onError(new RuntimeException("publisher failed"));
            runPendingTasks(channel);

            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void cancelledSubscriberImmediatelyCancelsAndValidatesNullSignals() {
        CancelledSubscriber<String> subscriber = new CancelledSubscriber<>();
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext("ignored");
        subscriber.onComplete();

        assertThat(subscription.cancelled()).isTrue();
        assertThatThrownBy(() -> subscriber.onSubscribe(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null subscription");
        assertThatThrownBy(() -> subscriber.onError(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Null error published");
    }

    private static void runPendingTasks(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
    }

    private static final class ForwardedMessageRecorder extends ChannelInboundHandlerAdapter {
        private final List<Object> messages = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            messages.add(msg);
        }

        List<Object> messages() {
            return messages;
        }
    }

    private static final class RecordingSubscriber<T> implements Subscriber<T> {
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final List<T> items = new CopyOnWriteArrayList<>();
        private final AtomicReference<Subscription> subscription = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription.set(subscription);
            subscribed.countDown();
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable error) {
            this.error.set(error);
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            completed.set(true);
            terminal.countDown();
        }

        void awaitSubscription() throws InterruptedException {
            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
        }

        void awaitTerminal() throws InterruptedException {
            assertThat(terminal.await(5, TimeUnit.SECONDS)).isTrue();
        }

        void request(long demand) {
            subscription.get().request(demand);
        }

        void cancel() {
            subscription.get().cancel();
        }

        List<T> items() {
            return items;
        }

        Throwable error() {
            return error.get();
        }

        boolean completed() {
            return completed.get();
        }
    }

    private static final class RecordingSubscription implements Subscription {
        private final List<Long> requests = new CopyOnWriteArrayList<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long demand) {
            requests.add(demand);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        List<Long> requests() {
            return requests;
        }

        boolean cancelled() {
            return cancelled.get();
        }
    }
}
