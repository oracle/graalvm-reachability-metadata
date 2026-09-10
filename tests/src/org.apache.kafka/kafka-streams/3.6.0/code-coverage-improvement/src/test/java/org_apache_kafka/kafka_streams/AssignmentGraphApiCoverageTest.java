/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.assignment.AssignorConfiguration;
import org.apache.kafka.streams.processor.internals.assignment.Graph;
import org.apache.kafka.streams.processor.internals.assignment.RackAwareTaskAssignor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises assignment flow solving and the empty-cluster rack-aware assignment contract. */
public class AssignmentGraphApiCoverageTest {
    @Test
    void shouldSolveMinCostFlowAndExposeResidualEdges() {
        Graph<Integer> graph = new Graph<>();
        graph.addEdge(0, 1, 1, 7, 1);
        graph.setSourceNode(0);
        graph.setSinkNode(1);
        assertThat(graph.nodes()).containsExactly(0, 1);
        assertThat(graph.edges(0)).containsKey(1);
        assertThat(graph.isResidualGraph()).isFalse();

        graph.solveMinCostFlow();
        assertThat(graph.totalCost()).isEqualTo(7L);
        Graph<Integer> residual = graph.residualGraph();
        assertThat(residual.isResidualGraph()).isTrue();
        assertThat(residual.nodes()).containsExactly(0, 1);
        assertThat(residual.edges(0)).containsKey(1);

        Graph<Integer>.Edge edge = graph.edges(0).get(1);
        Graph<Integer>.Edge same = graph.new Edge(1, 1, 7, 0, 1);
        assertThat(same).isEqualTo(graph.new Edge(1, 1, 7, 0, 1));
        assertThat(edge.toString()).contains("1");
        assertThat(edge.hashCode()).isEqualTo(edge.hashCode());
    }

    @Test
    void shouldInspectRackAwareStateAndOptimizeEmptyAssignments() throws Exception {
        RackAwareTaskAssignor assignor = new RackAwareTaskAssignor(
                Cluster.empty(), Map.of(), Map.of(), Map.of(), Map.of(), null,
                assignmentConfigs(), org.apache.kafka.common.utils.Time.SYSTEM);
        Set<String> topics = new java.util.HashSet<>();
        assertThat(assignor.racksForPartition()).isEmpty();
        assertThat(assignor.populateTopicsToDescribe(topics, false)).isTrue();
        assertThat(topics).isEmpty();
        assertThat(assignor.optimizeActiveTasks(new TreeSet<>(), new TreeMap<>(), 0, 0)).isZero();
        assertThat(assignor.optimizeStandbyTasks(new TreeMap<>(), 0, 0,
                (source, destination, task, clients) -> true)).isZero();

        TaskId task = new TaskId(6, 0);
        TopicPartition partition = new TopicPartition("rack-topic", 0);
        Node node = new Node(0, "rack-host", 9092, "rack-a");
        Cluster cluster = new Cluster("rack-cluster", List.of(node),
                List.of(new PartitionInfo("rack-topic", 0, node, new Node[] {node}, new Node[] {node})),
                Set.of(), Set.of());
        UUID clientId = UUID.randomUUID();
        org.apache.kafka.streams.processor.internals.assignment.ClientState client =
                new org.apache.kafka.streams.processor.internals.assignment.ClientState(
                        Set.of(), Set.of(), Map.of(), Map.of(), 1, clientId);
        client.assignActive(task);
        RackAwareTaskAssignor populated = new RackAwareTaskAssignor(cluster,
                Map.of(task, Set.of(partition)), Map.of(), Map.of(),
                Map.of(clientId, Map.of("rack", Optional.of("rack-a"))), null,
                rackAssignmentConfigs(), org.apache.kafka.common.utils.Time.SYSTEM);
        assertThat(populated.canEnableRackAwareAssignor()).isTrue();
        assertThat(populated.optimizeActiveTasks(new TreeSet<>(Set.of(task)),
                new TreeMap<>(Map.of(clientId, client)), 0, 0)).isGreaterThanOrEqualTo(0L);
    }

    private static AssignorConfiguration.AssignmentConfigs rackAssignmentConfigs() throws Exception {
        Constructor<AssignorConfiguration.AssignmentConfigs> constructor =
                AssignorConfiguration.AssignmentConfigs.class.getDeclaredConstructor(
                        Long.class, Integer.class, Integer.class, Long.class, List.class,
                        Integer.class, Integer.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0L, 1, 1, 60_000L, new ArrayList<String>(), 1, 1, "min_traffic");
    }

    private static AssignorConfiguration.AssignmentConfigs assignmentConfigs() throws Exception {
        Constructor<AssignorConfiguration.AssignmentConfigs> constructor =
                AssignorConfiguration.AssignmentConfigs.class.getDeclaredConstructor(
                        Long.class, Integer.class, Integer.class, Long.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0L, 1, 1, 60_000L, new ArrayList<String>());
    }
}
