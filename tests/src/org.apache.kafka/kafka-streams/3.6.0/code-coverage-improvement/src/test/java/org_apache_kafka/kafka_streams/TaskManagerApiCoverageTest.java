/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ReadOnlyTask;
import org.apache.kafka.streams.processor.internals.StandbyTask;
import org.apache.kafka.streams.processor.internals.StreamTask;
import org.apache.kafka.streams.processor.internals.Task;
import org.apache.kafka.streams.processor.internals.TaskExecutionMetadata;
import org.apache.kafka.streams.processor.internals.TasksRegistry;
import org.apache.kafka.streams.processor.internals.tasks.DefaultTaskManager;
import org.apache.kafka.streams.processor.internals.tasks.TaskExecutor;
import org.apache.kafka.streams.processor.internals.tasks.TaskExecutorCreator;
import org.apache.kafka.streams.internals.StreamsConfigUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises an empty task manager through its public coordination operations. */
public class TaskManagerApiCoverageTest {
    @Test
    void shouldCoordinateAnEmptyTaskSetAndDrainFailures() {
        StreamsConfig config = streamsConfig();
        TaskExecutor executor = new EmptyTaskExecutor();
        TaskExecutorCreator creator = (manager, name, time, metadata) -> executor;
        TaskExecutionMetadata metadata = new TaskExecutionMetadata(
                Set.of(), Set.of(), StreamsConfigUtils.ProcessingMode.AT_LEAST_ONCE);
        DefaultTaskManager manager = new DefaultTaskManager(
                Time.SYSTEM, "coverage-manager", new EmptyTasksRegistry(), config, creator, metadata);
        TaskId taskId = new TaskId(5, 2);
        StreamsException failure = new StreamsException("task failed");

        assertThat(manager.getTasks()).isEmpty();
        assertThat(manager.assignNextTask(executor)).isNull();
        assertThat(manager.lockTasks(Set.of())).isNotNull();
        KafkaFuture<Void> allLocked = manager.lockAllTasks();
        assertThat(allLocked).isNotNull();
        manager.unlockTasks(Set.of(taskId));
        manager.unlockAllTasks();
        manager.add(Set.of());
        assertThatThrownBy(() -> manager.remove(taskId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not locked");
        assertThatThrownBy(() -> manager.setUncaughtException(failure, taskId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only be set");
        assertThat(manager.drainUncaughtExceptions()).isEmpty();
    }

    private static StreamsConfig streamsConfig() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "task-manager-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        properties.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, "1");
        return new StreamsConfig(properties);
    }

    private static final class EmptyTaskExecutor implements TaskExecutor {
        @Override public String name() {
            return "empty";
        }
        @Override public void start() { }
        @Override public void shutdown(Duration timeout) { }
        @Override public ReadOnlyTask currentTask() {
            return null;
        }
        @Override public KafkaFuture<StreamTask> unassign() {
            return KafkaFuture.completedFuture(null);
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
        @Override public void replaceActiveWithStandby(StandbyTask task) { }
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
