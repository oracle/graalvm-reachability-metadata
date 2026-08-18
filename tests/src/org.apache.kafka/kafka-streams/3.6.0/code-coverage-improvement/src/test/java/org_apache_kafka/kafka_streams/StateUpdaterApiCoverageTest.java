/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ChangelogReader;
import org.apache.kafka.streams.processor.internals.DefaultStateUpdater;
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder;
import org.apache.kafka.streams.processor.internals.ProcessorStateManager;
import org.apache.kafka.streams.processor.internals.StateUpdater;
import org.apache.kafka.streams.processor.internals.Task;
import org.apache.kafka.streams.processor.internals.TopologyMetadata;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the empty state-updater lifecycle and its value object contract. */
public class StateUpdaterApiCoverageTest {
    @Test
    void shouldStartStopAndExposeEmptyUpdaterQueues() {
        StreamsConfig config = streamsConfig();
        ChangelogReader changelogReader = new EmptyChangelogReader();
        TopologyMetadata topologyMetadata = new TopologyMetadata(new InternalTopologyBuilder(), config);
        Metrics metrics = new Metrics();
        DefaultStateUpdater updater = new DefaultStateUpdater(
                "coverage-updater", metrics, config, changelogReader, topologyMetadata, Time.SYSTEM);

        try {
            assertThat(updater.getTasks()).isEmpty();
            assertThat(updater.getActiveTasks()).isEmpty();
            assertThat(updater.getStandbyTasks()).isEmpty();
            assertThat(updater.getUpdatingTasks()).isEmpty();
            assertThat(updater.getUpdatingStandbyTasks()).isEmpty();
            assertThat(updater.getRestoredActiveTasks()).isEmpty();
            assertThat(updater.getPausedTasks()).isEmpty();
            assertThat(updater.getRemovedTasks()).isEmpty();
            assertThat(updater.getExceptionsAndFailedTasks()).isEmpty();
            assertThat(updater.restoresActiveTasks()).isFalse();
            assertThat(updater.hasRemovedTasks()).isFalse();
            assertThat(updater.hasExceptionsAndFailedTasks()).isFalse();
            assertThat(updater.drainRestoredActiveTasks(Duration.ZERO)).isEmpty();
            assertThat(updater.drainRemovedTasks()).isEmpty();
            assertThat(updater.drainExceptionsAndFailedTasks()).isEmpty();

            updater.signalResume();
            updater.start();
            updater.start();
            updater.shutdown(Duration.ofSeconds(2));
            updater.shutdown(Duration.ZERO);
        } finally {
            updater.shutdown(Duration.ofSeconds(2));
            metrics.close();
        }
    }

    @Test
    void shouldRetainAndDrainAQueuedTaskRemoval() {
        StreamsConfig config = streamsConfig();
        TopologyMetadata topologyMetadata = new TopologyMetadata(new InternalTopologyBuilder(), config);
        Metrics metrics = new Metrics();
        DefaultStateUpdater updater = new DefaultStateUpdater(
                "coverage-updater-removal", metrics, config, new EmptyChangelogReader(), topologyMetadata, Time.SYSTEM);
        Task task = new SimpleTask(new TaskId(4, 1));

        try {
            updater.add(task);
            assertThat(updater.getTasks()).hasSize(1);
            updater.shutdown(Duration.ZERO);
            assertThat(updater.hasRemovedTasks()).isTrue();
            assertThat(updater.drainRemovedTasks()).containsExactly(task);
            assertThat(updater.hasRemovedTasks()).isFalse();
            updater.remove(task.id());
            updater.signalResume();
        } finally {
            updater.shutdown(Duration.ZERO);
            metrics.close();
        }
    }

    @Test
    void shouldCompareExceptionAndTasksByValue() {
        RuntimeException exception = new RuntimeException("restore failed");
        Set<Task> tasks = Set.of(new SimpleTask(new TaskId(4, 1)));
        StateUpdater.ExceptionAndTasks first = new StateUpdater.ExceptionAndTasks(tasks, exception);
        StateUpdater.ExceptionAndTasks same = new StateUpdater.ExceptionAndTasks(tasks, exception);

        assertThat(first.getTasks()).containsExactlyElementsOf(tasks);
        assertThat(first.exception()).isSameAs(exception);
        assertThat(first).isEqualTo(same);
        assertThat(first.hashCode()).isEqualTo(same.hashCode());
    }

    private static StreamsConfig streamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "state-updater-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "1");
        properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        return new StreamsConfig(properties);
    }

    private static final class EmptyChangelogReader implements ChangelogReader {
        @Override public void register(TopicPartition partition, ProcessorStateManager stateManager) { }
        @Override public void register(Set<TopicPartition> partitions, ProcessorStateManager stateManager) { }
        @Override public void unregister(Collection<TopicPartition> partitions) { }
        @Override public long restore(Map<TaskId, Task> restoringTasks) {
            return 0L;
        }
        @Override public void enforceRestoreActive() { }
        @Override public void transitToUpdateStandby() { }
        @Override public boolean isRestoringActive() {
            return false;
        }
        @Override public Set<TopicPartition> completedChangelogs() {
            return Set.of();
        }
        @Override public boolean allChangelogsCompleted() {
            return true;
        }
        @Override public void clear() { }
        @Override public boolean isEmpty() {
            return true;
        }
    }

    private static final class SimpleTask implements Task {
        private final TaskId id;
        private State state = State.RUNNING;

        SimpleTask(TaskId id) {
            this.id = id;
        }

        @Override public void initializeIfNeeded() {
            state = State.RUNNING;
        }
        @Override public void completeRestoration(Consumer<Set<TopicPartition>> callback) { }
        @Override public void suspend() {
            state = State.SUSPENDED;
        }
        @Override public void resume() {
            state = State.RUNNING;
        }
        @Override public void closeDirty() {
            state = State.CLOSED;
        }
        @Override public void closeClean() {
            state = State.CLOSED;
        }
        @Override public void updateInputPartitions(Set<TopicPartition> partitions, Map<String, List<String>> nodeToSourceTopics) { }
        @Override public void maybeCheckpoint(boolean enforceCheckpoint) { }
        @Override public void markChangelogAsCorrupted(Collection<TopicPartition> partitions) { }
        @Override public void revive() {
            state = State.CREATED;
        }
        @Override public void prepareRecycle() {
            state = State.CLOSED;
        }
        @Override public void addRecords(TopicPartition partition, Iterable<ConsumerRecord<byte[], byte[]>> records) { }
        @Override public Map<TopicPartition, OffsetAndMetadata> prepareCommit() {
            return Map.of();
        }
        @Override public void postCommit(boolean enforceCheckpoint) { }
        @Override public void maybeInitTaskTimeoutOrThrow(long wallClockTime, Exception cause) { }
        @Override public void clearTaskTimeout() { }
        @Override public void recordRestoration(Time time, long numRestored, boolean end) { }
        @Override public TaskId id() {
            return id;
        }
        @Override public boolean isActive() {
            return false;
        }
        @Override public Set<TopicPartition> inputPartitions() {
            return Set.of();
        }
        @Override public Set<TopicPartition> changelogPartitions() {
            return Set.of();
        }
        @Override public State state() {
            return state;
        }
        @Override public ProcessorStateManager stateManager() {
            return null;
        }
        @Override public boolean commitNeeded() {
            return false;
        }
        @Override public StateStore getStore(String name) {
            return null;
        }
        @Override public Map<TopicPartition, Long> changelogOffsets() {
            return Map.of();
        }
        @Override public Map<TopicPartition, Long> committedOffsets() {
            return Map.of();
        }
        @Override public Map<TopicPartition, Long> highWaterMark() {
            return Map.of();
        }
        @Override public Optional<Long> timeCurrentIdlingStarted() {
            return Optional.empty();
        }
    }
}
