/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ProcessorStateManager;
import org.apache.kafka.streams.processor.internals.ReadOnlyTask;
import org.apache.kafka.streams.processor.internals.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies that the read-only task adapter exposes state while rejecting mutations. */
public class ReadOnlyTaskApiCoverageTest {
    private static final TopicPartition INPUT = new TopicPartition("input", 0);
    private static final TaskId TASK_ID = new TaskId(2, 3);

    @Test
    void shouldExposeTheUnderlyingTaskState() {
        RecordingTask delegate = new RecordingTask();
        ReadOnlyTask task = new ReadOnlyTask(delegate);

        assertThat(task.id()).isEqualTo(TASK_ID);
        assertThat(task.isActive()).isFalse();
        assertThat(task.inputPartitions()).containsExactly(INPUT);
        assertThat(task.changelogPartitions()).containsExactly(INPUT);
        assertThat(task.state()).isEqualTo(Task.State.RUNNING);
        assertThat(task.commitRequested()).isTrue();
        assertThat(task.needsInitializationOrRestoration()).isFalse();
        assertThat(task.changelogOffsets()).containsEntry(INPUT, 7L);
    }

    @Test
    void shouldRejectOperationsThatCouldChangeTheTask() {
        ReadOnlyTask task = new ReadOnlyTask(new RecordingTask());

        assertReadOnly(() -> task.initializeIfNeeded());
        assertReadOnly(() -> task.addPartitionsForOffsetReset(Set.of(INPUT)));
        assertReadOnly(() -> task.completeRestoration(ignored -> { }));
        assertReadOnly(task::suspend);
        assertReadOnly(task::resume);
        assertReadOnly(task::closeDirty);
        assertReadOnly(task::closeClean);
        assertReadOnly(() -> task.updateInputPartitions(Set.of(INPUT), Map.of()));
        assertReadOnly(() -> task.maybeCheckpoint(true));
        assertReadOnly(() -> task.markChangelogAsCorrupted(List.of(INPUT)));
        assertReadOnly(task::revive);
        assertReadOnly(task::prepareRecycle);
        assertReadOnly(() -> task.addRecords(INPUT, List.of()));
        assertReadOnly(() -> task.process(10L));
        assertReadOnly(() -> task.recordProcessBatchTime(10L));
        assertReadOnly(() -> task.recordProcessTimeRatioAndBufferSize(10L, 2L));
        assertReadOnly(task::maybePunctuateStreamTime);
        assertReadOnly(task::maybePunctuateSystemTime);
        assertReadOnly(task::prepareCommit);
        assertReadOnly(() -> task.postCommit(true));
        assertReadOnly(task::purgeableOffsets);
        assertReadOnly(() -> task.maybeInitTaskTimeoutOrThrow(10L, new RuntimeException("timeout")));
        assertReadOnly(task::clearTaskTimeout);
        assertReadOnly(() -> task.recordRestoration(Time.SYSTEM, 10L, true));
        assertReadOnly(task::commitNeeded);
        assertReadOnly(() -> task.getStore("store"));
        assertReadOnly(task::committedOffsets);
        assertReadOnly(task::highWaterMark);
        assertReadOnly(task::timeCurrentIdlingStarted);
        assertReadOnly(task::stateManager);
    }

    private static void assertReadOnly(Runnable operation) {
        assertThatThrownBy(() -> operation.run())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("This task is read-only");
    }

    private static void assertReadOnly(BooleanSupplier operation) {
        assertThatThrownBy(operation::getAsBoolean)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("This task is read-only");
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private static final class RecordingTask implements Task {
        @Override
        public void initializeIfNeeded() { }

        @Override
        public void completeRestoration(Consumer<Set<TopicPartition>> callback) { }

        @Override
        public void suspend() { }

        @Override
        public void resume() { }

        @Override
        public void closeDirty() { }

        @Override
        public void closeClean() { }

        @Override
        public void updateInputPartitions(Set<TopicPartition> inputPartitions,
                                          Map<String, List<String>> storeNameToSourceTopics) { }

        @Override
        public void maybeCheckpoint(boolean enforce) { }

        @Override
        public void markChangelogAsCorrupted(java.util.Collection<TopicPartition> partitions) { }

        @Override
        public void revive() { }

        @Override
        public void prepareRecycle() { }

        @Override
        public void addRecords(TopicPartition partition,
                               Iterable<org.apache.kafka.clients.consumer.ConsumerRecord<byte[], byte[]>> records) { }

        @Override
        public Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> prepareCommit() {
            return Map.of();
        }

        @Override
        public void postCommit(boolean enforce) { }

        @Override
        public void maybeInitTaskTimeoutOrThrow(long wallClockTime, Exception cause) { }

        @Override
        public void clearTaskTimeout() { }

        @Override
        public void recordRestoration(Time time, long numRecords, boolean endOfRestore) { }

        @Override
        public TaskId id() {
            return TASK_ID;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public Set<TopicPartition> inputPartitions() {
            return Set.of(INPUT);
        }

        @Override
        public Set<TopicPartition> changelogPartitions() {
            return Set.of(INPUT);
        }

        @Override
        public State state() {
            return State.RUNNING;
        }

        @Override
        public ProcessorStateManager stateManager() {
            return null;
        }

        @Override
        public boolean commitNeeded() {
            return true;
        }

        @Override
        public boolean commitRequested() {
            return true;
        }

        @Override
        public StateStore getStore(String name) {
            return null;
        }

        @Override
        public Map<TopicPartition, Long> changelogOffsets() {
            return Map.of(INPUT, 7L);
        }

        @Override
        public Map<TopicPartition, Long> committedOffsets() {
            return Map.of();
        }

        @Override
        public Map<TopicPartition, Long> highWaterMark() {
            return Map.of();
        }

        @Override
        public Optional<Long> timeCurrentIdlingStarted() {
            return Optional.empty();
        }
    }
}
