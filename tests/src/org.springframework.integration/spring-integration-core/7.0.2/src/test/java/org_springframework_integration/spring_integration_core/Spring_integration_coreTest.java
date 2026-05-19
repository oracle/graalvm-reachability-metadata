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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.aggregator.DefaultAggregatingMessageGroupProcessor;
import org.springframework.integration.aggregator.MessageCountReleaseStrategy;
import org.springframework.integration.channel.DefaultHeaderChannelRegistry;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.FixedSubscriberChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.channel.PartitionedChannel;
import org.springframework.integration.channel.PriorityChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.RendezvousChannel;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.context.IntegrationContextUtils;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.filter.MessageFilter;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.router.HeaderValueRouter;
import org.springframework.integration.router.PayloadTypeRouter;
import org.springframework.integration.router.RecipientListRouter;
import org.springframework.integration.selector.MessageSelectorChain;
import org.springframework.integration.selector.MetadataStoreSelector;
import org.springframework.integration.selector.PayloadTypeSelector;
import org.springframework.integration.selector.UnexpiredMessageSelector;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.store.MessageGroup;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.integration.support.AbstractIntegrationMessageBuilder;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.locks.DefaultLockRegistry;
import org.springframework.integration.transformer.ClaimCheckInTransformer;
import org.springframework.integration.transformer.ClaimCheckOutTransformer;
import org.springframework.integration.transformer.HeaderEnricher;
import org.springframework.integration.transformer.HeaderFilter;
import org.springframework.integration.transformer.MessageTransformingHandler;
import org.springframework.integration.transformer.ObjectToStringTransformer;
import org.springframework.integration.transformer.PayloadTypeConvertingTransformer;
import org.springframework.integration.transformer.support.StaticHeaderValueMessageProcessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

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
    void messagingTemplateSupportsRequestReplyAndPollableReceives() {
        DirectChannel requests = new DirectChannel();
        QueueChannel replies = new QueueChannel();
        requests.subscribe(message -> {
            MessageChannel replyChannel = (MessageChannel) message.getHeaders().getReplyChannel();
            replyChannel.send(MessageBuilder.withPayload("processed-" + message.getPayload())
                    .setHeader("requestId", message.getHeaders().get("requestId"))
                    .build());
        });
        MessagingTemplate template = new MessagingTemplate();
        template.setSendTimeout(1_000);
        template.setReceiveTimeout(1_000);

        Message<?> reply = template.sendAndReceive(requests, MessageBuilder.withPayload("template")
                .setHeader("requestId", "r-1")
                .build());

        assertThat(reply).isNotNull();
        assertThat(reply.getPayload()).isEqualTo("processed-template");
        assertThat(reply.getHeaders().get("requestId")).isEqualTo("r-1");

        replies.send(MessageBuilder.withPayload(42).build());
        assertThat(template.receiveAndConvert(replies, 0)).isEqualTo(42);
    }

    @Test
    void rendezvousFixedSubscriberNullAndHeaderRegistryChannelsUsePublicChannelContracts() throws Exception {
        RendezvousChannel rendezvous = new RendezvousChannel();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> sender = executor.submit(() -> rendezvous.send(MessageBuilder.withPayload("handoff")
                .build(), 2_000));
        try {
            Message<?> received = rendezvous.receive(2_000);

            assertThat(received).isNotNull();
            assertThat(received.getPayload()).isEqualTo("handoff");
            assertThat(sender.get(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        AtomicReference<Message<?>> fixedSubscriberMessage = new AtomicReference<>();
        FixedSubscriberChannel fixedSubscriberChannel = new FixedSubscriberChannel(fixedSubscriberMessage::set);
        assertThat(fixedSubscriberChannel.send(MessageBuilder.withPayload("fixed").build())).isTrue();
        assertThat(fixedSubscriberMessage.get().getPayload()).isEqualTo("fixed");

        NullChannel nullChannel = new NullChannel();
        assertThat(nullChannel.send(MessageBuilder.withPayload("discard").build())).isTrue();
        assertThat(nullChannel.receive(0)).isNull();

        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
        DefaultListableBeanFactory registryBeanFactory = new DefaultListableBeanFactory();
        registryBeanFactory.registerSingleton(IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME, taskScheduler);
        DefaultHeaderChannelRegistry registry = initialize(
                new DefaultHeaderChannelRegistry(10_000), registryBeanFactory);
        try {
            registry.setRemoveOnGet(true);
            Object channelName = registry.channelToChannelName(fixedSubscriberChannel);
            assertThat(channelName).isInstanceOf(String.class);
            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.channelNameToChannel((String) channelName)).isSameAs(fixedSubscriberChannel);
            assertThat(registry.size()).isZero();
        } finally {
            registry.stop();
            taskScheduler.shutdown();
        }
    }

    @Test
    void payloadAndHeaderRoutersResolveMappedChannelsFromBeanFactory() {
        QueueChannel stringPayloads = new QueueChannel();
        QueueChannel numberPayloads = new QueueChannel();
        QueueChannel standardPriority = new QueueChannel();
        QueueChannel expressPriority = new QueueChannel();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("stringPayloads", stringPayloads);
        beanFactory.registerSingleton("numberPayloads", numberPayloads);
        beanFactory.registerSingleton("standardPriority", standardPriority);
        beanFactory.registerSingleton("expressPriority", expressPriority);

        PayloadTypeRouter payloadTypeRouter = new PayloadTypeRouter();
        payloadTypeRouter.setChannelMapping(String.class.getName(), "stringPayloads");
        payloadTypeRouter.setChannelMapping(Integer.class.getName(), "numberPayloads");
        initialize(payloadTypeRouter, beanFactory);

        payloadTypeRouter.handleMessage(MessageBuilder.withPayload("text").build());
        payloadTypeRouter.handleMessage(MessageBuilder.withPayload(99).build());

        assertThat(stringPayloads.receive(0).getPayload()).isEqualTo("text");
        assertThat(numberPayloads.receive(0).getPayload()).isEqualTo(99);

        HeaderValueRouter headerValueRouter = new HeaderValueRouter("shippingPriority");
        headerValueRouter.setChannelMapping("standard", "standardPriority");
        headerValueRouter.setChannelMapping("express", "expressPriority");
        initialize(headerValueRouter, beanFactory);

        headerValueRouter.handleMessage(MessageBuilder.withPayload("first")
                .setHeader("shippingPriority", "standard")
                .build());
        headerValueRouter.handleMessage(MessageBuilder.withPayload("second")
                .setHeader("shippingPriority", "express")
                .build());

        assertThat(standardPriority.receive(0).getPayload()).isEqualTo("first");
        assertThat(expressPriority.receive(0).getPayload()).isEqualTo("second");
    }

    @Test
    void headerTransformersEnrichFilterAndRenderPayloads() {
        HeaderEnricher enricher = new HeaderEnricher(Map.of(
                "tenant", new StaticHeaderValueMessageProcessor<>("north"),
                "internalTrace", new StaticHeaderValueMessageProcessor<>("trace-1")));
        enricher.setDefaultOverwrite(true);
        initialize(enricher);

        Message<?> enriched = enricher.transform(MessageBuilder.withPayload(List.of("a", "b"))
                .setHeader("tenant", "original")
                .build());

        assertThat(enriched.getHeaders().get("tenant")).isEqualTo("north");
        assertThat(enriched.getHeaders().get("internalTrace")).isEqualTo("trace-1");

        HeaderFilter headerFilter = new HeaderFilter("internal*");
        headerFilter.setPatternMatch(true);
        initialize(headerFilter);
        Message<?> filtered = headerFilter.transform(enriched);

        assertThat(filtered.getHeaders()).containsEntry("tenant", "north");
        assertThat(filtered.getHeaders()).doesNotContainKey("internalTrace");

        ObjectToStringTransformer objectToStringTransformer = initialize(new ObjectToStringTransformer());
        Message<?> rendered = objectToStringTransformer.transform(filtered);

        assertThat(rendered.getPayload()).isEqualTo("[a, b]");
        assertThat(rendered.getHeaders().get("tenant")).isEqualTo("north");
    }

    @Test
    void metadataStoreSelectorsAndSelectorChainsFilterMessagesDeterministically() {
        SimpleMetadataStore metadataStore = new SimpleMetadataStore();
        MessageProcessor<String> keyProcessor = message -> message.getHeaders().get("businessKey", String.class);
        MessageProcessor<String> valueProcessor = message -> message.getPayload().toString();
        MetadataStoreSelector selector = new MetadataStoreSelector(keyProcessor, valueProcessor, metadataStore);
        selector.setCompareValues((oldValue, newValue) -> newValue.compareTo(oldValue) > 0);

        Message<String> firstVersion = MessageBuilder.withPayload("v1")
                .setHeader("businessKey", "order-1")
                .build();
        Message<String> duplicateVersion = MessageBuilder.withPayload("v1")
                .setHeader("businessKey", "order-1")
                .build();
        Message<String> laterVersion = MessageBuilder.withPayload("v2")
                .setHeader("businessKey", "order-1")
                .build();

        assertThat(selector.accept(firstVersion)).isTrue();
        assertThat(selector.accept(duplicateVersion)).isFalse();
        assertThat(selector.accept(laterVersion)).isTrue();
        assertThat(metadataStore.get("order-1")).isEqualTo("v2");

        MessageSelectorChain chain = new MessageSelectorChain();
        chain.add(new PayloadTypeSelector(String.class));
        chain.add(new UnexpiredMessageSelector());

        assertThat(chain.accept(MessageBuilder.withPayload("fresh")
                .setExpirationDate(System.currentTimeMillis() + 60_000)
                .build())).isTrue();
        assertThat(chain.accept(MessageBuilder.withPayload(7)
                .setExpirationDate(System.currentTimeMillis() + 60_000)
                .build())).isFalse();
        assertThat(chain.accept(MessageBuilder.withPayload("expired")
                .setExpirationDate(System.currentTimeMillis() - 1_000)
                .build())).isFalse();
    }

    @Test
    void defaultLockRegistryCoordinatesAccessAcrossThreads() throws Exception {
        DefaultLockRegistry registry = new DefaultLockRegistry();
        Lock lock = registry.obtain("customer-42");
        lock.lock();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> contender = executor.submit(() -> {
                Lock contenderLock = registry.obtain("customer-42");
                boolean acquired = contenderLock.tryLock(100, TimeUnit.MILLISECONDS);
                if (acquired) {
                    contenderLock.unlock();
                }
                return acquired;
            });

            assertThat(contender.get(2, TimeUnit.SECONDS)).isFalse();
        } finally {
            lock.unlock();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }

        Lock reacquired = registry.obtain("customer-42");
        assertThat(reacquired.tryLock(1, TimeUnit.SECONDS)).isTrue();
        reacquired.unlock();
    }

    @Test
    void sourcePollingChannelAdapterPollsMessageSourceIntoOutputChannel() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(IntegrationContextUtils.TASK_SCHEDULER_BEAN_NAME, taskScheduler);
        QueueChannel output = new QueueChannel();
        AtomicInteger counter = new AtomicInteger();
        MessageSource<String> source = () -> {
            int value = counter.incrementAndGet();
            if (value > 2) {
                return null;
            }
            return MessageBuilder.withPayload("poll-" + value)
                    .setHeader("pollNumber", value)
                    .build();
        };
        SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
        adapter.setSource(source);
        adapter.setOutputChannel(output);
        adapter.setTrigger(new PeriodicTrigger(Duration.ofMillis(10)));
        adapter.setMaxMessagesPerPoll(1);
        initialize(adapter, beanFactory);

        adapter.start();
        try {
            Message<?> first = output.receive(2_000);
            Message<?> second = output.receive(2_000);

            assertThat(first).isNotNull();
            assertThat(first.getPayload()).isEqualTo("poll-1");
            assertThat(first.getHeaders().get("pollNumber")).isEqualTo(1);
            assertThat(second).isNotNull();
            assertThat(second.getPayload()).isEqualTo("poll-2");
            assertThat(second.getHeaders().get("pollNumber")).isEqualTo(2);
        } finally {
            adapter.stop();
            adapter.destroy();
            taskScheduler.shutdown();
        }
    }

    @Test
    void partitionedChannelSerializesMessagesByPartitionKeyOnDedicatedWorkers() throws Exception {
        AtomicInteger threadCounter = new AtomicInteger();
        PartitionedChannel channel = new PartitionedChannel(2, message -> message.getHeaders().get("partitionKey"));
        channel.setThreadFactory(task -> {
            Thread thread = new Thread(task, "partition-worker-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        initialize(channel);

        CountDownLatch handledMessages = new CountDownLatch(4);
        List<String> handledPayloads = new CopyOnWriteArrayList<>();
        Map<Object, String> threadsByPartition = new ConcurrentHashMap<>();
        AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
        channel.subscribe(message -> {
            Object partitionKey = message.getHeaders().get("partitionKey");
            String threadName = Thread.currentThread().getName();
            String previousThreadName = threadsByPartition.putIfAbsent(partitionKey, threadName);
            if (previousThreadName != null && !previousThreadName.equals(threadName)) {
                handlerFailure.compareAndSet(null, new AssertionError(
                        "Partition " + partitionKey + " moved from " + previousThreadName + " to " + threadName));
            }
            handledPayloads.add(message.getPayload().toString());
            handledMessages.countDown();
        });

        try {
            assertThat(channel.send(MessageBuilder.withPayload("zero-one").setHeader("partitionKey", 0).build()))
                    .isTrue();
            assertThat(channel.send(MessageBuilder.withPayload("one-one").setHeader("partitionKey", 1).build()))
                    .isTrue();
            assertThat(channel.send(MessageBuilder.withPayload("zero-two").setHeader("partitionKey", 0).build()))
                    .isTrue();
            assertThat(channel.send(MessageBuilder.withPayload("one-two").setHeader("partitionKey", 1).build()))
                    .isTrue();

            assertThat(handledMessages.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handlerFailure.get()).isNull();
            assertThat(threadsByPartition).containsOnlyKeys(0, 1);
            assertThat(threadsByPartition.get(0)).isNotEqualTo(threadsByPartition.get(1));
            assertThat(handledPayloads.indexOf("zero-one")).isLessThan(handledPayloads.indexOf("zero-two"));
            assertThat(handledPayloads.indexOf("one-one")).isLessThan(handledPayloads.indexOf("one-two"));
        } finally {
            channel.destroy();
        }
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
        return initialize(component, BEAN_FACTORY);
    }

    private static <T extends IntegrationObjectSupport> T initialize(
            T component, DefaultListableBeanFactory beanFactory) {
        component.setBeanFactory(beanFactory);
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
