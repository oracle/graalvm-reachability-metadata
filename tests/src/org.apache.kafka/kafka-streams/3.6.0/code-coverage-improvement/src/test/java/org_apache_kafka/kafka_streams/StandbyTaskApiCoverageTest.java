/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.kafka.streams.processor.internals;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyConfig;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises standby-task lifecycle behavior through the public {@link Task} contract. */
public class StandbyTaskApiCoverageTest {
    private static final TopicPartition INPUT = new TopicPartition("standby-input", 0);

    @Test
    void shouldInitializeRestoreInspectAndRecycleStandbyTask() {
        TestTaskResources resources = new TestTaskResources();
        Task task = resources.newTask();

        assertThat(task.isActive()).isFalse();
        assertThat(task.state()).isEqualTo(Task.State.CREATED);
        task.initializeIfNeeded();
        assertThat(task.state()).isEqualTo(Task.State.RUNNING);

        task.recordRestoration(Time.SYSTEM, 3L, false);
        assertThatThrownBy(() -> task.recordRestoration(Time.SYSTEM, 1L, true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(task.prepareCommit()).isEmpty();
        task.postCommit(false);
        assertThat(task.commitNeeded()).isFalse();
        assertThat(task.changelogOffsets()).isEmpty();
        assertThat(task.committedOffsets()).isEmpty();
        assertThat(task.highWaterMark()).isEmpty();
        assertThat(task.timeCurrentIdlingStarted()).isEmpty();
        assertThatThrownBy(() -> task.addRecords(new TopicPartition("other", 0), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> task.completeRestoration(ignored -> { }))
                .isInstanceOf(IllegalStateException.class);

        task.suspend();
        assertThat(task.state()).isEqualTo(Task.State.SUSPENDED);
        task.resume();
        assertThat(task.state()).isEqualTo(Task.State.SUSPENDED);
        task.prepareRecycle();
        assertThat(task.state()).isEqualTo(Task.State.CLOSED);
        resources.close();
    }

    @Test
    void shouldCloseInitializedStandbyTaskUsingBothClosePolicies() {
        TestTaskResources cleanResources = new TestTaskResources();
        Task cleanTask = cleanResources.newTask();
        cleanTask.initializeIfNeeded();
        cleanTask.suspend();
        cleanTask.closeClean();
        assertThat(cleanTask.state()).isEqualTo(Task.State.CLOSED);
        cleanResources.close();

        TestTaskResources dirtyResources = new TestTaskResources();
        Task dirtyTask = dirtyResources.newTask();
        dirtyTask.initializeIfNeeded();
        dirtyTask.suspend();
        dirtyTask.closeDirty();
        assertThat(dirtyTask.state()).isEqualTo(Task.State.CLOSED);
        dirtyResources.close();
    }

    private static final class TestTaskResources {
        private final StreamsConfig config;
        private final Metrics metrics = new Metrics();
        private final StreamsMetricsImpl streamsMetrics;
        private final StateDirectory stateDirectory;
        private final ThreadCache cache;
        private final InternalMockProcessorContext<?, ?> context;
        private final ProcessorStateManager stateManager;

        TestTaskResources() {
            Properties properties = new Properties();
            properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "standby-api");
            properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.Serdes$ByteArraySerde");
            properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.Serdes$ByteArraySerde");
            config = new StreamsConfig(properties);
            streamsMetrics = new StreamsMetricsImpl(metrics, "standby-api", "standby-thread", Time.SYSTEM);
            stateDirectory = new StateDirectory(config, Time.SYSTEM, false, false);
            cache = new ThreadCache(new LogContext("standby "), 1024L, streamsMetrics);
            context = new InternalMockProcessorContext<>(new File("build/standby-api"),
                    Serdes.ByteArray(), Serdes.ByteArray(), config);
            context.initialize();
            stateManager = new ProcessorStateManager(new TaskId(0, 0), Task.TaskType.STANDBY, false,
                    new LogContext("standby "), stateDirectory, new EmptyChangelogRegister(), Map.of(), Set.of(INPUT), false);
        }

        Task newTask() {
            TopologyConfig.TaskConfig taskConfig = new TopologyConfig(config).getTaskConfig();
            ProcessorTopology topology = new ProcessorTopology(
                    List.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of(), Set.of());
            return new StandbyTask(new TaskId(0, 0), Set.of(INPUT), topology, taskConfig,
                    streamsMetrics, stateManager, stateDirectory, cache, context);
        }

        void close() {
            stateManager.close();
            stateDirectory.close();
            metrics.close();
        }
    }

    private static final class EmptyChangelogRegister implements ChangelogRegister {
        @Override public void register(TopicPartition partition, ProcessorStateManager stateManager) { }
        @Override public void register(Set<TopicPartition> partitions, ProcessorStateManager stateManager) { }
        @Override public void unregister(java.util.Collection<TopicPartition> partitions) { }
    }
}
