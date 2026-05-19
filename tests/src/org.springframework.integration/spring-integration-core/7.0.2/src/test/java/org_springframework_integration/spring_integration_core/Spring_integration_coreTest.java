/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.MessageCountReleaseStrategy;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.PriorityChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.filter.MessageFilter;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.router.RecipientListRouter;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.integration.support.AbstractIntegrationMessageBuilder;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.ClaimCheckInTransformer;
import org.springframework.integration.transformer.ClaimCheckOutTransformer;
import org.springframework.integration.transformer.MessageTransformingHandler;
import org.springframework.integration.transformer.PayloadTypeConvertingTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class Spring_integration_coreTest {

    private static final DefaultListableBeanFactory BEAN_FACTORY = new DefaultListableBeanFactory();

    @Test
    void directChannelInvokesSubscribersInterceptorsAndWireTap() {
        DirectChannel input = new DirectChannel();
        QueueChannel tapChannel = new QueueChannel();
        List<String> callbacks = new ArrayList<>();
        AtomicReference<Message<?>> handledMessage = new AtomicReference<>();

        input.addInterceptor(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                callbacks.add("preSend");
                return message;
            }

            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                callbacks.add("postSend:" + sent);
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                callbacks.add("afterSendCompletion:" + sent);
            }

        });
        input.addInterceptor(new WireTap(tapChannel));
        input.subscribe(message -> {
            callbacks.add("handler");
            handledMessage.set(message);
        });

        Message<String> message = MessageBuilder.withPayload("orders")
                .setHeader("tenant", "test")
                .build();

        assertThat(input.send(message)).isTrue();

        Message<?> tapped = tapChannel.receive(0);
        assertThat(tapped).isNotNull();
        assertThat(tapped.getPayload()).isEqualTo("orders");
        assertThat(tapped.getHeaders().get("tenant")).isEqualTo("test");
        assertThat(handledMessage.get()).isSameAs(message);
        assertThat(callbacks)
                .containsExactly("preSend", "handler", "postSend:true", "afterSendCompletion:true");
    }

    @Test
    void queueChannelsApplyCapacityPurgeAndPriorityOrdering() {
        QueueChannel boundedQueue = new QueueChannel(2);
        Message<String> first = MessageBuilder.withPayload("first").build();
        Message<String> drop = MessageBuilder.withPayload("drop").build();
        Message<String> overflow = MessageBuilder.withPayload("overflow").build();

        assertThat(boundedQueue.send(first)).isTrue();
        assertThat(boundedQueue.send(drop)).isTrue();
        assertThat(boundedQueue.send(overflow, 0)).isFalse();
        assertThat(boundedQueue.getQueueSize()).isEqualTo(2);

        List<Message<?>> purged = boundedQueue.purge(message -> !"drop".equals(message.getPayload()));
        assertThat(purged).hasSize(1);
        assertThat(purged.get(0).getPayload()).isEqualTo("drop");
        assertThat(boundedQueue.receive(0)).isSameAs(first);
        assertThat(boundedQueue.receive(0)).isNull();

        PriorityChannel priorityChannel = new PriorityChannel();
        priorityChannel.send(MessageBuilder.withPayload("low").setPriority(1).build());
        priorityChannel.send(MessageBuilder.withPayload("high").setPriority(9).build());

        assertThat(priorityChannel.receive(0).getPayload()).isEqualTo("high");
        assertThat(priorityChannel.receive(0).getPayload()).isEqualTo("low");
    }

    @Test
    void publishSubscribeChannelBroadcastsWithSequenceHeaders() {
        PublishSubscribeChannel channel = new PublishSubscribeChannel();
        channel.setApplySequence(true);
        List<Message<?>> firstSubscriberMessages = new ArrayList<>();
        List<Message<?>> secondSubscriberMessages = new ArrayList<>();
        channel.subscribe(firstSubscriberMessages::add);
        channel.subscribe(secondSubscriberMessages::add);

        assertThat(channel.send(MessageBuilder.withPayload("broadcast").build())).isTrue();

        assertThat(firstSubscriberMessages).hasSize(1);
        assertThat(secondSubscriberMessages).hasSize(1);
        Message<?> first = firstSubscriberMessages.get(0);
        Message<?> second = secondSubscriberMessages.get(0);
        assertThat(first.getPayload()).isEqualTo("broadcast");
        assertThat(second.getPayload()).isEqualTo("broadcast");
        assertThat(first.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE)).isEqualTo(2);
        assertThat(second.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_SIZE)).isEqualTo(2);
        assertThat(first.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER)).isEqualTo(1);
        assertThat(second.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER)).isEqualTo(2);
    }

    @Test
    void eventDrivenConsumerInvokesServiceActivatorAndSendsReply() {
        DirectChannel requests = new DirectChannel();
        QueueChannel replies = new QueueChannel();
        MessageProcessor<String> processor = message -> message.getPayload().toString().toUpperCase(Locale.ROOT);
        ServiceActivatingHandler handler = new ServiceActivatingHandler(processor);
        handler.setOutputChannel(replies);
        initialize(handler);
        EventDrivenConsumer consumer = new EventDrivenConsumer(requests, handler);
        initialize(consumer);

        consumer.start();
        try {
            assertThat(requests.send(MessageBuilder.withPayload("spring").build())).isTrue();

            Message<?> reply = replies.receive(1_000);
            assertThat(reply).isNotNull();
            assertThat(reply.getPayload()).isEqualTo("SPRING");
        } finally {
            consumer.stop();
            consumer.destroy();
        }
    }

    @Test
    void filterAndTransformerRouteAcceptedRejectedAndConvertedMessages() {
        QueueChannel accepted = new QueueChannel();
        QueueChannel discarded = new QueueChannel();
        MessageFilter filter = new MessageFilter(message -> message.getPayload().toString().startsWith("keep"));
        filter.setOutputChannel(accepted);
        filter.setDiscardChannel(discarded);
        filter.setThrowExceptionOnRejection(false);
        initialize(filter);
        filter.start();
        try {
            filter.handleMessage(MessageBuilder.withPayload("keep-this").build());
            filter.handleMessage(MessageBuilder.withPayload("drop-this").build());

            assertThat(accepted.receive(0).getPayload()).isEqualTo("keep-this");
            assertThat(discarded.receive(0).getPayload()).isEqualTo("drop-this");
        } finally {
            filter.stop();
        }

        QueueChannel transformed = new QueueChannel();
        PayloadTypeConvertingTransformer<String, Integer> transformer = new PayloadTypeConvertingTransformer<>();
        transformer.setConverter(String::length);
        initialize(transformer);
        MessageTransformingHandler transformingHandler = new MessageTransformingHandler(transformer);
        transformingHandler.setOutputChannel(transformed);
        initialize(transformingHandler);
        transformingHandler.start();
        try {
            transformingHandler.handleMessage(MessageBuilder.withPayload("integration").build());

            assertThat(transformed.receive(0).getPayload()).isEqualTo(11);
        } finally {
            transformingHandler.stop();
        }
    }

    @Test
    void claimCheckStoresAndRestoresMessagesById() {
        SimpleMessageStore store = new SimpleMessageStore();
        ClaimCheckInTransformer claimCheckIn = new ClaimCheckInTransformer(store);
        ClaimCheckOutTransformer claimCheckOut = new ClaimCheckOutTransformer(store);
        claimCheckOut.setRemoveMessage(true);
        initialize(claimCheckIn);
        initialize(claimCheckOut);
        Message<String> original = MessageBuilder.withPayload("invoice")
                .setHeader("tenant", "north")
                .build();

        Message<?> claim = claimCheckIn.transform(original);

        assertThat(claim.getPayload()).isInstanceOf(UUID.class);
        UUID claimId = (UUID) claim.getPayload();
        assertThat(claimId).isEqualTo(original.getHeaders().getId());
        assertThat(store.getMessage(claimId)).isSameAs(original);

        Message<?> restored = claimCheckOut.transform(MessageBuilder.withPayload(claimId)
                .setHeader("request", "restore")
                .build());

        assertThat(restored.getPayload()).isEqualTo("invoice");
        assertThat(restored.getHeaders().get("tenant")).isEqualTo("north");
        assertThat(restored.getHeaders().get("request")).isEqualTo("restore");
        assertThat(store.getMessage(claimId)).isNull();
    }

    @Test
    void recipientListRouterSendsMessagesToMatchingRecipientsAndDefaultChannel() {
        QueueChannel stringMessages = new QueueChannel();
        QueueChannel springMessages = new QueueChannel();
        QueueChannel unmatchedMessages = new QueueChannel();
        RecipientListRouter router = new RecipientListRouter();
        router.addRecipient(stringMessages, message -> message.getPayload() instanceof String);
        router.addRecipient(springMessages, message -> message.getPayload().toString().contains("spring"));
        router.setDefaultOutputChannel(unmatchedMessages);
        initialize(router);

        router.handleMessage(MessageBuilder.withPayload("spring integration").build());
        router.handleMessage(MessageBuilder.withPayload(7).build());

        assertThat(stringMessages.receive(0).getPayload()).isEqualTo("spring integration");
        assertThat(springMessages.receive(0).getPayload()).isEqualTo("spring integration");
        assertThat(unmatchedMessages.receive(0).getPayload()).isEqualTo(7);
        assertThat(stringMessages.receive(0)).isNull();
        assertThat(springMessages.receive(0)).isNull();
        assertThat(unmatchedMessages.receive(0)).isNull();
    }

    @Test
    void splitterAndAggregatorProcessMessageGroups() {
        QueueChannel splitMessages = new QueueChannel();
        DefaultMessageSplitter splitter = new DefaultMessageSplitter();
        splitter.setApplySequence(true);
        splitter.setOutputChannel(splitMessages);
        initialize(splitter);

        splitter.handleMessage(MessageBuilder.withPayload(List.of("alpha", "bravo", "charlie")).build());

        Message<?> firstSplit = splitMessages.receive(0);
        Message<?> secondSplit = splitMessages.receive(0);
        Message<?> thirdSplit = splitMessages.receive(0);
        assertThat(firstSplit.getPayload()).isEqualTo("alpha");
        assertThat(secondSplit.getPayload()).isEqualTo("bravo");
        assertThat(thirdSplit.getPayload()).isEqualTo("charlie");
        assertThat(firstSplit.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER)).isEqualTo(1);
        assertThat(secondSplit.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER)).isEqualTo(2);
        assertThat(thirdSplit.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER)).isEqualTo(3);

        SimpleMessageStore store = new SimpleMessageStore();
        store.addMessagesToGroup("letters", firstSplit, secondSplit, thirdSplit);
        MessageGroup group = store.getMessageGroup("letters");
        assertThat(group.size()).isEqualTo(3);
        assertThat(new MessageCountReleaseStrategy(3).canRelease(group)).isTrue();
        assertThat(new MessageCountReleaseStrategy(4).canRelease(group)).isFalse();

        Object aggregate = new DefaultAggregatingMessageGroupProcessor().processMessageGroup(group);

        Collection<?> aggregatePayload = extractPayloadCollection(aggregate);
        List<Object> aggregateValues = new ArrayList<>();
        aggregateValues.addAll(aggregatePayload);
        assertThat(aggregateValues).containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }

    @Test
    void executorAndFluxChannelsDeliverMessagesAsynchronouslyWithinBoundedWaits() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorChannel executorChannel = new ExecutorChannel(executor);
        CountDownLatch executorLatch = new CountDownLatch(1);
        AtomicReference<String> executorPayload = new AtomicReference<>();
        executorChannel.subscribe(message -> {
            executorPayload.set(message.getPayload().toString());
            executorLatch.countDown();
        });
        try {
            assertThat(executorChannel.send(MessageBuilder.withPayload("executor").build())).isTrue();
            assertThat(executorLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(executorPayload.get()).isEqualTo("executor");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        FluxMessageChannel fluxChannel = new FluxMessageChannel();
        CountDownLatch fluxLatch = new CountDownLatch(2);
        List<String> fluxPayloads = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> fluxError = new AtomicReference<>();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        fluxChannel.subscribe(new Subscriber<>() {

            @Override
            public void onSubscribe(Subscription currentSubscription) {
                subscription.set(currentSubscription);
                currentSubscription.request(2);
            }

            @Override
            public void onNext(Message<?> message) {
                fluxPayloads.add(message.getPayload().toString());
                fluxLatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                fluxError.set(throwable);
            }

            @Override
            public void onComplete() {
            }

        });

        fluxChannel.start();
        try {
            assertThat(fluxChannel.send(MessageBuilder.withPayload("flux-one").build())).isTrue();
            assertThat(fluxChannel.send(MessageBuilder.withPayload("flux-two").build())).isTrue();
            assertThat(fluxLatch.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(fluxError.get()).isNull();
            assertThat(fluxPayloads).containsExactly("flux-one", "flux-two");
        } finally {
            Subscription currentSubscription = subscription.get();
            if (currentSubscription != null) {
                currentSubscription.cancel();
            }
            fluxChannel.destroy();
        }
    }

    private static <T extends IntegrationObjectSupport> T initialize(T component) {
        component.setBeanFactory(BEAN_FACTORY);
        component.afterPropertiesSet();
        return component;
    }

    private static Collection<?> extractPayloadCollection(Object aggregate) {
        Object payload;
        if (aggregate instanceof Message<?> message) {
            payload = message.getPayload();
        } else if (aggregate instanceof AbstractIntegrationMessageBuilder<?> builder) {
            payload = builder.build().getPayload();
        } else {
            payload = aggregate;
        }
        if (payload instanceof Collection<?> collection) {
            return collection;
        }
        fail("Expected aggregate payload to be a Collection but was <%s>", payload);
        return List.of();
    }

}
