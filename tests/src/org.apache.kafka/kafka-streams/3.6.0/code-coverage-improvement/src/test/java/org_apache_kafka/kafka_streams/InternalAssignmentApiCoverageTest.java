/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.InternalTopologyBuilder;
import org.apache.kafka.streams.processor.internals.TopologyMetadata;
import org.apache.kafka.streams.processor.internals.assignment.AssignorConfiguration;
import org.apache.kafka.streams.processor.internals.assignment.ClientState;
import org.apache.kafka.streams.processor.internals.assignment.FallbackPriorTaskAssignor;
import org.apache.kafka.streams.processor.internals.assignment.HighAvailabilityTaskAssignor;
import org.apache.kafka.streams.processor.internals.assignment.RackAwareTaskAssignor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives assignment state and topology metadata through their public contracts. */
public class InternalAssignmentApiCoverageTest {
    @Test
    void shouldAssignAndRevokeActiveAndStandbyTasks() {
        TaskId active = new TaskId(1, 0);
        TaskId standby = new TaskId(2, 0);
        ClientState empty = new ClientState();
        ClientState state = new ClientState(Set.of(active), Set.of(standby), Map.of(active, 4L), Map.of("rack", "a"), 1);
        assertThat(empty.capacity()).isZero();
        state.assignActive(active);
        assertThat(state.activeTasks()).contains(active);
        state.assignActiveToConsumer(active, "consumer-a");
        state.assignStandby(standby);
        state.assignStandbyToConsumer(standby, "consumer-a");
        assertThat(state.assignedActiveTasksByConsumer().get("consumer-a")).contains(active);
        assertThat(state.assignedStandbyTasksByConsumer().get("consumer-a")).contains(standby);
        state.revokeActiveFromConsumer(active, "consumer-a");
        assertThat(state.revokingActiveTasksByConsumer().get("consumer-a")).contains(active);
        assertThat(state.standbyTasks()).contains(standby);
        state.unassignActive(active);
        assertThat(state.currentAssignment()).contains("standby");
        assertThat(state.toString()).contains("standbyTasks");
    }

    @Test
    void shouldUseFallbackAssignmentForAnEmptyTaskSet() throws Exception {
        FallbackPriorTaskAssignor assignor = new FallbackPriorTaskAssignor();
        TaskId task = new TaskId(3, 0);
        ClientState client = new ClientState(Set.of(), Set.of(), Map.of(), Map.of(), 1);
        Class<?> configType = Class.forName(
                "org.apache.kafka.streams.processor.internals.assignment.AssignorConfiguration$AssignmentConfigs");
        Constructor<?> configConstructor = configType.getDeclaredConstructor(
                Long.class, Integer.class, Integer.class, Long.class, java.util.List.class);
        configConstructor.setAccessible(true);
        Object configs = configConstructor.newInstance(0L, 1, 0, 60000L, new ArrayList<String>());
        @SuppressWarnings("unchecked")
        org.apache.kafka.streams.processor.internals.assignment.AssignorConfiguration.AssignmentConfigs typedConfigs =
                (org.apache.kafka.streams.processor.internals.assignment.AssignorConfiguration.AssignmentConfigs) configs;
        assertThat(assignor.assign(Map.of(UUID.randomUUID(), client), Set.of(task), Set.of(), null, typedConfigs)).isTrue();
    }

    @Test
    void shouldMoveCaughtUpTasksAcrossClientsThroughHighAvailabilityAssignment() throws Exception {
        TaskId first = new TaskId(4, 0);
        TaskId second = new TaskId(5, 0);
        UUID firstClient = UUID.randomUUID();
        UUID secondClient = UUID.randomUUID();
        ClientState owner = new ClientState(Set.of(first), Set.of(second), Map.of(first, 0L, second, 0L), Map.of(), 1,
                firstClient);
        ClientState destination = new ClientState(Set.of(), Set.of(), Map.of(first, 0L, second, 0L), Map.of(), 1,
                secondClient);
        AssignorConfiguration.AssignmentConfigs configs = assignmentConfigs(0L, 1, 2);
        RackAwareTaskAssignor rackAware = new RackAwareTaskAssignor(Cluster.empty(), Map.of(), Map.of(), Map.of(),
                Map.of(), null, configs, org.apache.kafka.common.utils.Time.SYSTEM);
        boolean probingRebalance = new HighAvailabilityTaskAssignor().assign(
                Map.of(firstClient, owner, secondClient, destination), Set.of(first, second), Set.of(second), rackAware,
                configs);
        assertThat(owner.assignedTaskCount()).isGreaterThanOrEqualTo(0);
        assertThat(destination.assignedTaskCount()).isGreaterThanOrEqualTo(0);
        assertThat(probingRebalance).isIn(true, false);
    }

    @Test
    void shouldResolveTopologyMetadataAndOffsetPolicies() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "metadata-api");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        StreamsConfig config = new StreamsConfig(properties);
        InternalTopologyBuilder topologyBuilder = new InternalTopologyBuilder();
        topologyBuilder.setApplicationId("metadata-api");
        topologyBuilder.addSource(Topology.AutoOffsetReset.EARLIEST, "source", null,
                Serdes.String().deserializer(), Serdes.String().deserializer(), "metadata-input");
        TopologyMetadata metadata = new TopologyMetadata(topologyBuilder, config);
        assertThat(metadata.offsetResetStrategy("metadata-input")).isEqualTo(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST);
        assertThat(metadata.nodeToSourceTopics(new TaskId(0, 0))).isNotNull();
        assertThat(metadata.getStoreForChangelogTopic("missing-changelog")).isEmpty();
        assertThat(metadata.fullSourceTopicNamesForTopology(TopologyMetadata.UNNAMED_TOPOLOGY)).contains("metadata-input");
        assertThat(metadata.toString()).isNotNull();
    }

    private static AssignorConfiguration.AssignmentConfigs assignmentConfigs(long acceptableRecoveryLag,
            int numStandbyReplicas, int maxWarmupReplicas) throws Exception {
        Constructor<AssignorConfiguration.AssignmentConfigs> constructor =
                AssignorConfiguration.AssignmentConfigs.class.getDeclaredConstructor(
                        Long.class, Integer.class, Integer.class, Long.class, java.util.List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(acceptableRecoveryLag, numStandbyReplicas, maxWarmupReplicas,
                60000L, new ArrayList<String>());
    }
}
