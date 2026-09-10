/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.internals.ChangelogRegister;
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder;
import org.apache.kafka.streams.processor.internals.ProcessorContextImpl;
import org.apache.kafka.streams.processor.internals.ProcessorStateManager;
import org.apache.kafka.streams.processor.internals.ProcessorTopology;
import org.apache.kafka.streams.processor.internals.StreamTask;
import org.apache.kafka.streams.processor.internals.StreamThread;
import org.apache.kafka.streams.processor.internals.Task;
import org.apache.kafka.streams.processor.internals.TaskManager;
import org.apache.kafka.streams.processor.internals.TasksRegistry;
import org.apache.kafka.streams.processor.internals.TopologyMetadata;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.processor.internals.StateDirectory;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives processor-context and rebalance lifecycle entries through concrete collaborators. */
public class DeepInternalLifecycleCoverageTest {
    @Test
    void shouldDelegateContextLifecycleToAnActiveStreamTask() {
        StreamsConfig config = streamsConfig();
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "lifecycle-client", "lifecycle-thread", Time.SYSTEM);
        StateDirectory stateDirectory = new StateDirectory(config, Time.SYSTEM, false, false);
        ThreadCache cache = new ThreadCache(new LogContext("lifecycle "), 1024L, streamsMetrics);
        ProcessorStateManager stateManager = new ProcessorStateManager(
                new TaskId(0, 0), Task.TaskType.ACTIVE, false, new LogContext("lifecycle "), stateDirectory,
                new EmptyChangelogRegister(), Map.of(), Set.of(), false);
        ProcessorContextImpl context = new ProcessorContextImpl(
                new TaskId(0, 0), config, stateManager, streamsMetrics, cache);
        MockConsumer<byte[], byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        org.apache.kafka.streams.processor.internals.RecordCollector collector = NativeCoverageFixtures.recordCollector();
        StreamTask task = new StreamTask(
                new TaskId(0, 0), Set.of(new TopicPartition("lifecycle", 0)), lifecycleTopology(), consumer,
                new TopologyConfig(config).getTaskConfig(),
                streamsMetrics, stateDirectory, cache, Time.SYSTEM, stateManager,
                collector, context, new LogContext("lifecycle "));
        try {
            assertThat(context.stateManager()).isSameAs(stateManager);
            assertThat(context.recordCollector()).isSameAs(collector);
            assertThat(context.currentStreamTimeMs()).isEqualTo(-1L);

            context.commit();
            assertThat(task.commitRequested()).isTrue();
        } finally {
            stateManager.close();
            stateDirectory.close();
            consumer.close();
            metrics.close();
        }
    }

    @Test
    void shouldSchedulePunctuationThroughAnInitializedStreamTask() {
        StreamsConfig config = streamsConfig();
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "schedule-client", "schedule-thread", Time.SYSTEM);
        StateDirectory stateDirectory = new StateDirectory(config, Time.SYSTEM, false, false);
        ThreadCache cache = new ThreadCache(new LogContext("schedule "), 1024L, streamsMetrics);
        ProcessorStateManager stateManager = new ProcessorStateManager(
                new TaskId(0, 0), Task.TaskType.ACTIVE, false, new LogContext("schedule "), stateDirectory,
                new EmptyChangelogRegister(), Map.of(), Set.of(), false);
        ProcessorContextImpl context = new ProcessorContextImpl(
                new TaskId(0, 0), config, stateManager, streamsMetrics, cache);
        MockConsumer<byte[], byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition partition = new TopicPartition("schedule", 0);
        consumer.assign(Set.of(partition));
        consumer.updateBeginningOffsets(Map.of(partition, 0L));
        consumer.updateEndOffsets(Map.of(partition, 0L));
        AtomicReference<Cancellable> scheduled = new AtomicReference<>();
        StreamTask task = new StreamTask(
                new TaskId(0, 0), Set.of(partition), schedulingTopology(scheduled), consumer,
                new TopologyConfig(config).getTaskConfig(), streamsMetrics, stateDirectory, cache, Time.SYSTEM,
                stateManager, NativeCoverageFixtures.recordCollector(), context, new LogContext("schedule "));
        try {
            task.initializeIfNeeded();
            task.completeRestoration(ignored -> { });
            assertThat(scheduled.get()).isNotNull();
            scheduled.get().cancel();
        } finally {
            stateManager.close();
            stateDirectory.close();
            consumer.close();
            metrics.close();
        }
    }

    @Test
    void shouldDelegateProducerBlockedTimeAndRevocationCallbacks() throws Exception {
        StreamsConfig config = streamsConfig();
        Metrics metrics = new Metrics();
        StreamsMetricsImpl streamsMetrics = new StreamsMetricsImpl(metrics, "rebalance-client", "rebalance-thread", Time.SYSTEM);
        TopologyMetadata topologyMetadata = new TopologyMetadata(new InternalTopologyBuilder(), config);
        Object activeTaskCreator = newActiveTaskCreator(config, streamsMetrics, topologyMetadata);
        TaskManager taskManager = newTaskManager(activeTaskCreator, topologyMetadata, config);
        assertThat(taskManager.totalProducerBlockedTime()).isZero();

        MockConsumer<byte[], byte[]> mainConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockConsumer<byte[], byte[]> restoreConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        StreamThread streamThread = new StreamThread(
                Time.SYSTEM, config, null, mainConsumer, restoreConsumer, null, "earliest", taskManager,
                streamsMetrics, topologyMetadata, "rebalance-thread", new LogContext("rebalance "),
                new AtomicInteger(), new AtomicLong(), new ConcurrentLinkedQueue<>(), () -> { },
                (error, recoverable) -> { }, size -> { });
        java.lang.reflect.Method rebalanceListenerMethod = StreamThread.class.getDeclaredMethod("rebalanceListener");
        rebalanceListenerMethod.setAccessible(true);
        ConsumerRebalanceListener rebalanceListener =
                (ConsumerRebalanceListener) rebalanceListenerMethod.invoke(streamThread);
        java.lang.reflect.Method setStateMethod = StreamThread.class.getDeclaredMethod(
                "setState", StreamThread.State.class);
        setStateMethod.setAccessible(true);
        setStateMethod.invoke(streamThread, StreamThread.State.STARTING);
        rebalanceListener.onPartitionsRevoked(ListOf.partition(new TopicPartition("lifecycle", 0)));
        assertThat(streamThread.state()).isEqualTo(StreamThread.State.PARTITIONS_REVOKED);

        mainConsumer.close();
        restoreConsumer.close();
        topologyMetadata.unregisterThread(streamThread.getName());
        metrics.close();
    }

    private static Object newActiveTaskCreator(StreamsConfig config, StreamsMetricsImpl streamsMetrics,
            TopologyMetadata topologyMetadata) throws Exception {
        Class<?> creatorClass = Class.forName("org.apache.kafka.streams.processor.internals.ActiveTaskCreator");
        java.lang.reflect.Constructor<?> constructor = creatorClass.getDeclaredConstructor(
                TopologyMetadata.class, StreamsConfig.class, StreamsMetricsImpl.class, StateDirectory.class,
                Class.forName("org.apache.kafka.streams.processor.internals.ChangelogReader"),
                ThreadCache.class, Time.class, org.apache.kafka.streams.KafkaClientSupplier.class,
                String.class, UUID.class, org.slf4j.Logger.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(topologyMetadata, config, streamsMetrics, null, null, null, Time.SYSTEM, null,
                "rebalance-thread", UUID.randomUUID(), org.slf4j.LoggerFactory.getLogger(
                        DeepInternalLifecycleCoverageTest.class), false);
    }

    private static TaskManager newTaskManager(Object activeTaskCreator, TopologyMetadata topologyMetadata,
            StreamsConfig config) throws Exception {
        Class<?> creatorClass = Class.forName("org.apache.kafka.streams.processor.internals.ActiveTaskCreator");
        java.lang.reflect.Constructor<TaskManager> constructor = TaskManager.class.getDeclaredConstructor(
                Time.class,
                Class.forName("org.apache.kafka.streams.processor.internals.ChangelogReader"),
                UUID.class, String.class, creatorClass,
                Class.forName("org.apache.kafka.streams.processor.internals.StandbyTaskCreator"),
                TasksRegistry.class, TopologyMetadata.class,
                org.apache.kafka.clients.admin.Admin.class, StateDirectory.class,
                Class.forName("org.apache.kafka.streams.processor.internals.StateUpdater"));
        constructor.setAccessible(true);
        return constructor.newInstance(Time.SYSTEM, null, UUID.randomUUID(), "rebalance", activeTaskCreator, null,
                new EmptyTasksRegistry(), topologyMetadata, null, null, null);
    }

    private static ProcessorTopology lifecycleTopology() {
        InternalTopologyBuilder builder = new InternalTopologyBuilder();
        builder.setApplicationId("deep-lifecycle");
        builder.addSource(null, "source", null, new StringDeserializer(), new StringDeserializer(), "lifecycle");
        return builder.buildTopology();
    }

    private static ProcessorTopology schedulingTopology(AtomicReference<Cancellable> scheduled) {
        InternalTopologyBuilder builder = new InternalTopologyBuilder();
        builder.setApplicationId("deep-lifecycle");
        builder.addSource(null, "source", null, new StringDeserializer(), new StringDeserializer(), "schedule");
        builder.addProcessor("schedule-processor", () -> new Processor<String, String, String, String>() {
            @Override
            public void init(ProcessorContext<String, String> processorContext) {
                scheduled.set(processorContext.schedule(Duration.ofMillis(1), PunctuationType.STREAM_TIME,
                        timestamp -> { }));
            }

            @Override
            public void process(Record<String, String> record) {
            }
        }, "source");
        return builder.buildTopology();
    }

    private static StreamsConfig streamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "deep-lifecycle");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE);
        return new StreamsConfig(properties);
    }

    private static final class ListOf {
        private ListOf() {
        }

        static <T> java.util.List<T> empty() {
            return java.util.List.of();
        }

        static <T> java.util.List<T> partition(T value) {
            return java.util.List.of(value);
        }
    }

    private static final class EmptyChangelogRegister implements ChangelogRegister {
        @Override
        public void register(TopicPartition partition, ProcessorStateManager stateManager) {
        }

        @Override
        public void register(Set<TopicPartition> partitions, ProcessorStateManager stateManager) {
        }

        @Override
        public void unregister(Collection<TopicPartition> partitions) {
        }
    }

    private static final class EmptyTasksRegistry implements TasksRegistry {
        @Override public Map<TaskId, Set<TopicPartition>> drainPendingActiveTasksForTopologies(Set<String> topologies) {
            return Map.of();
        }
        @Override public Map<TaskId, Set<TopicPartition>> drainPendingStandbyTasksForTopologies(Set<String> topologies) {
            return Map.of();
        }
        @Override public void addPendingActiveTasksToCreate(Map<TaskId, Set<TopicPartition>> tasks) { }
        @Override public void addPendingStandbyTasksToCreate(Map<TaskId, Set<TopicPartition>> tasks) { }
        @Override public void clearPendingTasksToCreate() { }
        @Override public Set<TopicPartition> removePendingTaskToRecycle(TaskId taskId) {
            return Set.of();
        }
        @Override public boolean hasPendingTasksToRecycle() {
            return false;
        }
        @Override public void addPendingTaskToRecycle(TaskId taskId, Set<TopicPartition> partitions) { }
        @Override public Set<TopicPartition> removePendingTaskToUpdateInputPartitions(TaskId taskId) {
            return Set.of();
        }
        @Override public void addPendingTaskToUpdateInputPartitions(TaskId taskId, Set<TopicPartition> partitions) { }
        @Override public boolean removePendingTaskToCloseDirty(TaskId taskId) {
            return false;
        }
        @Override public void addPendingTaskToCloseDirty(TaskId taskId) { }
        @Override public boolean removePendingTaskToCloseClean(TaskId taskId) {
            return false;
        }
        @Override public void addPendingTaskToCloseClean(TaskId taskId) { }
        @Override public Set<Task> drainPendingTasksToInit() {
            return Set.of();
        }
        @Override public void addPendingTasksToInit(Collection<Task> tasks) { }
        @Override public boolean hasPendingTasksToInit() {
            return false;
        }
        @Override public boolean removePendingActiveTaskToSuspend(TaskId taskId) {
            return false;
        }
        @Override public void addPendingActiveTaskToSuspend(TaskId taskId) { }
        @Override public void addActiveTasks(Collection<Task> tasks) { }
        @Override public void addStandbyTasks(Collection<Task> tasks) { }
        @Override public void addTask(Task task) { }
        @Override public void removeTask(Task task) { }
        @Override public void replaceActiveWithStandby(org.apache.kafka.streams.processor.internals.StandbyTask task) { }
        @Override public void replaceStandbyWithActive(StreamTask task) { }
        @Override public boolean updateActiveTaskInputPartitions(Task task, Set<TopicPartition> partitions) {
            return false;
        }
        @Override public void clear() { }
        @Override public Task activeTasksForInputPartition(TopicPartition partition) {
            return null;
        }
        @Override public Task task(TaskId taskId) {
            return null;
        }
        @Override public Collection<Task> tasks(Collection<TaskId> taskIds) {
            return Collections.emptyList();
        }
        @Override public Collection<Task> activeTasks() {
            return Collections.emptyList();
        }
        @Override public Set<Task> allTasks() {
            return Set.of();
        }
        @Override public Map<TaskId, Task> allTasksPerId() {
            return Map.of();
        }
        @Override public Set<TaskId> allTaskIds() {
            return Set.of();
        }
        @Override public boolean contains(TaskId taskId) {
            return false;
        }
    }
}
