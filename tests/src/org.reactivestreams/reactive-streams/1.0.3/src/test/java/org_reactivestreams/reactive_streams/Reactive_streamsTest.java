/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_reactivestreams.reactive_streams;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class Reactive_streamsTest {

    @Test
    void flowAdaptersRejectNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toPublisher(null))
                .withMessage("flowPublisher");
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toFlowPublisher(null))
                .withMessage("reactiveStreamsPublisher");
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toProcessor(null))
                .withMessage("flowProcessor");
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toFlowProcessor(null))
                .withMessage("reactiveStreamsProcessor");
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toFlowSubscriber(null))
                .withMessage("reactiveStreamsSubscriber");
        assertThatNullPointerException()
                .isThrownBy(() -> FlowAdapters.toSubscriber(null))
                .withMessage("flowSubscriber");
    }

    @Test
    void adaptersPassThroughNullSubscribersAndSubscriptions() {
        ReactivePublisherProbe<String> reactivePublisher = new ReactivePublisherProbe<>();
        FlowAdapters.toFlowPublisher(reactivePublisher).subscribe(null);

        assertThat(reactivePublisher.subscribeCalled).isTrue();
        assertThat(reactivePublisher.subscriber).isNull();

        FlowPublisherProbe<String> flowPublisher = new FlowPublisherProbe<>();
        FlowAdapters.toPublisher(flowPublisher).subscribe(null);

        assertThat(flowPublisher.subscribeCalled).isTrue();
        assertThat(flowPublisher.subscriber).isNull();

        RecordingReactiveSubscriber<String> reactiveSubscriber = new RecordingReactiveSubscriber<>();
        FlowAdapters.toFlowSubscriber(reactiveSubscriber).onSubscribe(null);

        assertThat(reactiveSubscriber.onSubscribeCalled).isTrue();
        assertThat(reactiveSubscriber.subscription).isNull();

        RecordingFlowSubscriber<String> flowSubscriber = new RecordingFlowSubscriber<>();
        FlowAdapters.toSubscriber(flowSubscriber).onSubscribe(null);

        assertThat(flowSubscriber.onSubscribeCalled).isTrue();
        assertThat(flowSubscriber.subscription).isNull();

        ReactiveProcessorProbe<Integer, String> reactiveProcessor = new ReactiveProcessorProbe<>();
        Flow.Processor<Integer, String> flowProcessor = FlowAdapters.toFlowProcessor(reactiveProcessor);
        flowProcessor.subscribe(null);
        flowProcessor.onSubscribe(null);

        assertThat(reactiveProcessor.subscribeCalled).isTrue();
        assertThat(reactiveProcessor.downstream).isNull();
        assertThat(reactiveProcessor.onSubscribeCalled).isTrue();
        assertThat(reactiveProcessor.upstream).isNull();

        FlowProcessorProbe<Integer, String> flowProcessorProbe = new FlowProcessorProbe<>();
        Processor<Integer, String> reactiveProcessorAdapter = FlowAdapters.toProcessor(flowProcessorProbe);
        reactiveProcessorAdapter.subscribe(null);
        reactiveProcessorAdapter.onSubscribe(null);

        assertThat(flowProcessorProbe.subscribeCalled).isTrue();
        assertThat(flowProcessorProbe.downstream).isNull();
        assertThat(flowProcessorProbe.onSubscribeCalled).isTrue();
        assertThat(flowProcessorProbe.upstream).isNull();
    }

    @Test
    void publisherAdaptersBridgeSignalsAndSubscriptionsInBothDirections() {
        ReactivePublisherProbe<String> reactivePublisher = new ReactivePublisherProbe<>();
        Flow.Publisher<String> flowPublisher = FlowAdapters.toFlowPublisher(reactivePublisher);
        RecordingFlowSubscriber<String> flowSubscriber = new RecordingFlowSubscriber<>();

        flowPublisher.subscribe(flowSubscriber);

        assertThat(reactivePublisher.subscriber).isNotNull();

        RecordingReactiveSubscription reactiveSubscription = new RecordingReactiveSubscription();
        reactivePublisher.emitSubscription(reactiveSubscription);
        reactivePublisher.emitNext("alpha");
        reactivePublisher.emitComplete();

        assertThat(flowSubscriber.values).containsExactly("alpha");
        assertThat(flowSubscriber.completed).isTrue();
        assertThat(flowSubscriber.subscription).isNotNull();

        flowSubscriber.subscription.request(3);
        flowSubscriber.subscription.cancel();

        assertThat(reactiveSubscription.requested).containsExactly(3L);
        assertThat(reactiveSubscription.cancelled).isTrue();

        FlowPublisherProbe<String> flowPublisherProbe = new FlowPublisherProbe<>();
        Publisher<String> reactivePublisherAdapter = FlowAdapters.toPublisher(flowPublisherProbe);
        RecordingReactiveSubscriber<String> reactiveSubscriber = new RecordingReactiveSubscriber<>();

        reactivePublisherAdapter.subscribe(reactiveSubscriber);

        assertThat(flowPublisherProbe.subscriber).isNotNull();

        RecordingFlowSubscription flowSubscription = new RecordingFlowSubscription();
        RuntimeException failure = new RuntimeException("boom");
        flowPublisherProbe.emitSubscription(flowSubscription);
        flowPublisherProbe.emitNext("beta");
        flowPublisherProbe.emitError(failure);

        assertThat(reactiveSubscriber.values).containsExactly("beta");
        assertThat(reactiveSubscriber.failure).isSameAs(failure);
        assertThat(reactiveSubscriber.subscription).isNotNull();

        reactiveSubscriber.subscription.request(5);
        reactiveSubscriber.subscription.cancel();

        assertThat(flowSubscription.requested).containsExactly(5L);
        assertThat(flowSubscription.cancelled).isTrue();
    }

    @Test
    void publisherAdaptersReuseExistingAdaptersAndDualImplementations() {
        ReactivePublisherProbe<String> reactivePublisher = new ReactivePublisherProbe<>();
        FlowPublisherProbe<String> flowPublisher = new FlowPublisherProbe<>();
        DualPublisher<String> dualPublisher = new DualPublisher<>();

        assertThat(FlowAdapters.toPublisher(FlowAdapters.toFlowPublisher(reactivePublisher))).isSameAs(reactivePublisher);
        assertThat(FlowAdapters.toFlowPublisher(FlowAdapters.toPublisher(flowPublisher))).isSameAs(flowPublisher);
        assertThat(FlowAdapters.toPublisher((Flow.Publisher<String>) dualPublisher)).isSameAs(dualPublisher);
        assertThat(FlowAdapters.toFlowPublisher((Publisher<String>) dualPublisher)).isSameAs(dualPublisher);
    }

    @Test
    void subscriberAdaptersBridgeSignalsAndSubscriptionsInBothDirections() {
        RecordingReactiveSubscriber<String> reactiveSubscriber = new RecordingReactiveSubscriber<>();
        Flow.Subscriber<String> flowSubscriber = FlowAdapters.toFlowSubscriber(reactiveSubscriber);
        RecordingFlowSubscription flowSubscription = new RecordingFlowSubscription();

        flowSubscriber.onSubscribe(flowSubscription);
        flowSubscriber.onNext("alpha");
        flowSubscriber.onComplete();

        assertThat(reactiveSubscriber.values).containsExactly("alpha");
        assertThat(reactiveSubscriber.completed).isTrue();
        assertThat(reactiveSubscriber.subscription).isNotNull();

        reactiveSubscriber.subscription.request(7);
        reactiveSubscriber.subscription.cancel();

        assertThat(flowSubscription.requested).containsExactly(7L);
        assertThat(flowSubscription.cancelled).isTrue();

        RecordingFlowSubscriber<String> flowSubscriberProbe = new RecordingFlowSubscriber<>();
        Subscriber<String> reactiveSubscriberAdapter = FlowAdapters.toSubscriber(flowSubscriberProbe);
        RecordingReactiveSubscription reactiveSubscription = new RecordingReactiveSubscription();
        IllegalStateException failure = new IllegalStateException("failure");

        reactiveSubscriberAdapter.onSubscribe(reactiveSubscription);
        reactiveSubscriberAdapter.onNext("beta");
        reactiveSubscriberAdapter.onError(failure);

        assertThat(flowSubscriberProbe.values).containsExactly("beta");
        assertThat(flowSubscriberProbe.failure).isSameAs(failure);
        assertThat(flowSubscriberProbe.subscription).isNotNull();

        flowSubscriberProbe.subscription.request(11);
        flowSubscriberProbe.subscription.cancel();

        assertThat(reactiveSubscription.requested).containsExactly(11L);
        assertThat(reactiveSubscription.cancelled).isTrue();
    }

    @Test
    void subscriberAdaptersReuseExistingAdaptersAndDualImplementations() {
        RecordingReactiveSubscriber<String> reactiveSubscriber = new RecordingReactiveSubscriber<>();
        RecordingFlowSubscriber<String> flowSubscriber = new RecordingFlowSubscriber<>();
        DualSubscriber<String> dualSubscriber = new DualSubscriber<>();

        assertThat(FlowAdapters.toSubscriber(FlowAdapters.toFlowSubscriber(reactiveSubscriber))).isSameAs(reactiveSubscriber);
        assertThat(FlowAdapters.toFlowSubscriber(FlowAdapters.toSubscriber(flowSubscriber))).isSameAs(flowSubscriber);
        assertThat(FlowAdapters.toSubscriber((Flow.Subscriber<String>) dualSubscriber)).isSameAs(dualSubscriber);
        assertThat(FlowAdapters.toFlowSubscriber((Subscriber<String>) dualSubscriber)).isSameAs(dualSubscriber);
    }

    @Test
    void processorAdaptersBridgeSignalsAndSubscriptionsInBothDirections() {
        ReactiveProcessorProbe<Integer, String> reactiveProcessor = new ReactiveProcessorProbe<>();
        Flow.Processor<Integer, String> flowProcessor = FlowAdapters.toFlowProcessor(reactiveProcessor);
        RecordingFlowSubscriber<String> flowSubscriber = new RecordingFlowSubscriber<>();

        flowProcessor.subscribe(flowSubscriber);

        assertThat(reactiveProcessor.downstream).isNotNull();

        RecordingReactiveSubscription downstreamReactiveSubscription = new RecordingReactiveSubscription();
        reactiveProcessor.emitSubscriptionToDownstream(downstreamReactiveSubscription);
        reactiveProcessor.emitNextToDownstream("alpha");
        reactiveProcessor.emitCompleteToDownstream();

        assertThat(flowSubscriber.values).containsExactly("alpha");
        assertThat(flowSubscriber.completed).isTrue();
        assertThat(flowSubscriber.subscription).isNotNull();

        flowSubscriber.subscription.request(13);
        flowSubscriber.subscription.cancel();

        assertThat(downstreamReactiveSubscription.requested).containsExactly(13L);
        assertThat(downstreamReactiveSubscription.cancelled).isTrue();

        RecordingFlowSubscription upstreamFlowSubscription = new RecordingFlowSubscription();
        flowProcessor.onSubscribe(upstreamFlowSubscription);
        flowProcessor.onNext(42);
        flowProcessor.onComplete();

        assertThat(reactiveProcessor.upstream).isNotNull();
        assertThat(reactiveProcessor.inputValues).containsExactly(42);
        assertThat(reactiveProcessor.inputCompleted).isTrue();

        reactiveProcessor.upstream.request(17);
        reactiveProcessor.upstream.cancel();

        assertThat(upstreamFlowSubscription.requested).containsExactly(17L);
        assertThat(upstreamFlowSubscription.cancelled).isTrue();

        FlowProcessorProbe<Integer, String> flowProcessorProbe = new FlowProcessorProbe<>();
        Processor<Integer, String> reactiveProcessorAdapter = FlowAdapters.toProcessor(flowProcessorProbe);
        RecordingReactiveSubscriber<String> reactiveSubscriber = new RecordingReactiveSubscriber<>();

        reactiveProcessorAdapter.subscribe(reactiveSubscriber);

        assertThat(flowProcessorProbe.downstream).isNotNull();

        RecordingFlowSubscription downstreamFlowSubscription = new RecordingFlowSubscription();
        flowProcessorProbe.emitSubscriptionToDownstream(downstreamFlowSubscription);
        flowProcessorProbe.emitNextToDownstream("beta");
        RuntimeException downstreamFailure = new RuntimeException("downstream");
        flowProcessorProbe.emitErrorToDownstream(downstreamFailure);

        assertThat(reactiveSubscriber.values).containsExactly("beta");
        assertThat(reactiveSubscriber.failure).isSameAs(downstreamFailure);
        assertThat(reactiveSubscriber.subscription).isNotNull();

        reactiveSubscriber.subscription.request(19);
        reactiveSubscriber.subscription.cancel();

        assertThat(downstreamFlowSubscription.requested).containsExactly(19L);
        assertThat(downstreamFlowSubscription.cancelled).isTrue();

        RecordingReactiveSubscription upstreamReactiveSubscription = new RecordingReactiveSubscription();
        reactiveProcessorAdapter.onSubscribe(upstreamReactiveSubscription);
        reactiveProcessorAdapter.onNext(64);
        IllegalArgumentException upstreamFailure = new IllegalArgumentException("upstream");
        reactiveProcessorAdapter.onError(upstreamFailure);

        assertThat(flowProcessorProbe.upstream).isNotNull();
        assertThat(flowProcessorProbe.inputValues).containsExactly(64);
        assertThat(flowProcessorProbe.inputFailure).isSameAs(upstreamFailure);

        flowProcessorProbe.upstream.request(23);
        flowProcessorProbe.upstream.cancel();

        assertThat(upstreamReactiveSubscription.requested).containsExactly(23L);
        assertThat(upstreamReactiveSubscription.cancelled).isTrue();
    }

    @Test
    void processorAdaptersReuseExistingAdaptersAndDualImplementations() {
        ReactiveProcessorProbe<Integer, String> reactiveProcessor = new ReactiveProcessorProbe<>();
        FlowProcessorProbe<Integer, String> flowProcessor = new FlowProcessorProbe<>();
        DualProcessor<Integer, String> dualProcessor = new DualProcessor<>();

        assertThat(FlowAdapters.toProcessor(FlowAdapters.toFlowProcessor(reactiveProcessor))).isSameAs(reactiveProcessor);
        assertThat(FlowAdapters.toFlowProcessor(FlowAdapters.toProcessor(flowProcessor))).isSameAs(flowProcessor);
        assertThat(FlowAdapters.toProcessor((Flow.Processor<Integer, String>) dualProcessor)).isSameAs(dualProcessor);
        assertThat(FlowAdapters.toFlowProcessor((Processor<Integer, String>) dualProcessor)).isSameAs(dualProcessor);
    }

    private static final class RecordingReactiveSubscription implements Subscription {
        private final List<Long> requested = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void request(long count) {
            requested.add(count);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class RecordingFlowSubscription implements Flow.Subscription {
        private final List<Long> requested = new ArrayList<>();
        private boolean cancelled;

        @Override
        public void request(long count) {
            requested.add(count);
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class ReactivePublisherProbe<T> implements Publisher<T> {
        private Subscriber<? super T> subscriber;
        private boolean subscribeCalled;

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscribeCalled = true;
            this.subscriber = subscriber;
        }

        private void emitSubscription(Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        private void emitNext(T value) {
            subscriber.onNext(value);
        }

        private void emitError(Throwable failure) {
            subscriber.onError(failure);
        }

        private void emitComplete() {
            subscriber.onComplete();
        }
    }

    private static final class FlowPublisherProbe<T> implements Flow.Publisher<T> {
        private Flow.Subscriber<? super T> subscriber;
        private boolean subscribeCalled;

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscribeCalled = true;
            this.subscriber = subscriber;
        }

        private void emitSubscription(Flow.Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        private void emitNext(T value) {
            subscriber.onNext(value);
        }

        private void emitError(Throwable failure) {
            subscriber.onError(failure);
        }

        private void emitComplete() {
            subscriber.onComplete();
        }
    }

    private static final class RecordingReactiveSubscriber<T> implements Subscriber<T> {
        private final List<T> values = new ArrayList<>();
        private Subscription subscription;
        private Throwable failure;
        private boolean completed;
        private boolean onSubscribeCalled;

        @Override
        public void onSubscribe(Subscription subscription) {
            onSubscribeCalled = true;
            this.subscription = subscription;
        }

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }

    private static final class RecordingFlowSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> values = new ArrayList<>();
        private Flow.Subscription subscription;
        private Throwable failure;
        private boolean completed;
        private boolean onSubscribeCalled;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            onSubscribeCalled = true;
            this.subscription = subscription;
        }

        @Override
        public void onNext(T item) {
            values.add(item);
        }

        @Override
        public void onError(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }

    private static final class ReactiveProcessorProbe<T, U> implements Processor<T, U> {
        private final List<T> inputValues = new ArrayList<>();
        private Subscription upstream;
        private Subscriber<? super U> downstream;
        private Throwable inputFailure;
        private boolean inputCompleted;
        private boolean subscribeCalled;
        private boolean onSubscribeCalled;

        @Override
        public void subscribe(Subscriber<? super U> subscriber) {
            subscribeCalled = true;
            downstream = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            onSubscribeCalled = true;
            upstream = subscription;
        }

        @Override
        public void onNext(T value) {
            inputValues.add(value);
        }

        @Override
        public void onError(Throwable failure) {
            inputFailure = failure;
        }

        @Override
        public void onComplete() {
            inputCompleted = true;
        }

        private void emitSubscriptionToDownstream(Subscription subscription) {
            downstream.onSubscribe(subscription);
        }

        private void emitNextToDownstream(U value) {
            downstream.onNext(value);
        }

        private void emitErrorToDownstream(Throwable failure) {
            downstream.onError(failure);
        }

        private void emitCompleteToDownstream() {
            downstream.onComplete();
        }
    }

    private static final class FlowProcessorProbe<T, U> implements Flow.Processor<T, U> {
        private final List<T> inputValues = new ArrayList<>();
        private Flow.Subscription upstream;
        private Flow.Subscriber<? super U> downstream;
        private Throwable inputFailure;
        private boolean inputCompleted;
        private boolean subscribeCalled;
        private boolean onSubscribeCalled;

        @Override
        public void subscribe(Flow.Subscriber<? super U> subscriber) {
            subscribeCalled = true;
            downstream = subscriber;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            onSubscribeCalled = true;
            upstream = subscription;
        }

        @Override
        public void onNext(T item) {
            inputValues.add(item);
        }

        @Override
        public void onError(Throwable failure) {
            inputFailure = failure;
        }

        @Override
        public void onComplete() {
            inputCompleted = true;
        }

        private void emitSubscriptionToDownstream(Flow.Subscription subscription) {
            downstream.onSubscribe(subscription);
        }

        private void emitNextToDownstream(U value) {
            downstream.onNext(value);
        }

        private void emitErrorToDownstream(Throwable failure) {
            downstream.onError(failure);
        }

        private void emitCompleteToDownstream() {
            downstream.onComplete();
        }
    }

    private static final class DualPublisher<T> implements Publisher<T>, Flow.Publisher<T> {
        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
        }
    }

    private static final class DualSubscriber<T> implements Subscriber<T>, Flow.Subscriber<T> {
        @Override
        public void onSubscribe(Subscription subscription) {
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
        }

        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable failure) {
        }

        @Override
        public void onComplete() {
        }
    }

    private static final class DualProcessor<T, U> implements Processor<T, U>, Flow.Processor<T, U> {
        @Override
        public void subscribe(Subscriber<? super U> subscriber) {
        }

        @Override
        public void subscribe(Flow.Subscriber<? super U> subscriber) {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
        }

        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable failure) {
        }

        @Override
        public void onComplete() {
        }
    }
}
