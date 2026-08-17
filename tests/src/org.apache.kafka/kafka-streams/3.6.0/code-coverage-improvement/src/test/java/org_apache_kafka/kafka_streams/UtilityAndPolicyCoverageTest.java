/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.InvalidStateStorePartitionException;
import org.apache.kafka.streams.errors.LockException;
import org.apache.kafka.streams.errors.ProcessorStateException;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.errors.StateStoreMigratedException;
import org.apache.kafka.streams.errors.StateStoreNotAvailableException;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsNotStartedException;
import org.apache.kafka.streams.errors.StreamsRebalancingException;
import org.apache.kafka.streams.errors.StreamsStoppedException;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskCorruptedException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.errors.TaskMigratedException;
import org.apache.kafka.streams.errors.TopologyException;
import org.apache.kafka.streams.errors.UnknownStateStoreException;
import org.apache.kafka.streams.errors.UnknownTopologyException;
import org.apache.kafka.streams.internals.ApiUtils;
import org.apache.kafka.streams.kstream.ForeachProcessor;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.internals.PendingUpdateAction;
import org.apache.kafka.streams.processor.internals.ProcessorMetadata;
import org.apache.kafka.streams.processor.internals.ToInternal;
import org.apache.kafka.streams.processor.internals.TopicPartitionMetadata;
import org.apache.kafka.streams.processor.internals.assignment.ClientState;
import org.apache.kafka.streams.query.Position;
import org.apache.kafka.streams.query.PositionBound;
import org.apache.kafka.streams.query.QueryResult;
import org.apache.kafka.streams.query.StateQueryResult;
import org.apache.kafka.streams.state.internals.Maybe;
import org.apache.kafka.streams.state.internals.Murmur3;
import org.apache.kafka.streams.state.internals.Murmur3.IncrementalHash32;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilityAndPolicyCoverageTest {
    @Test
    void hashingOptionalAndQueryUtilitiesHaveValueSemantics() {
        byte[] bytes = "kafka-streams".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(Murmur3.hash32(bytes)).isEqualTo(Murmur3.hash32(bytes));
        assertThat(Murmur3.hash32(17L)).isNotEqualTo(Murmur3.hash32(18L));
        assertThat(Murmur3.hash32(17L, 18L)).isNotEqualTo(Murmur3.hash32(18L, 17L));
        assertThat(Murmur3.hash64(bytes)).isEqualTo(Murmur3.hash64(bytes));
        IncrementalHash32 incremental = new IncrementalHash32();
        incremental.start(19);
        incremental.add(bytes, 0, 5);
        incremental.add(bytes, 5, bytes.length - 5);
        assertThat(incremental.end()).isEqualTo(Murmur3.hash32(bytes, 0, bytes.length, 19));
        assertThat(Maybe.defined("value")).hasSameHashCodeAs(Maybe.defined("value"));
        assertThat(Maybe.undefined().hashCode()).isEqualTo(Maybe.undefined().hashCode());

        assertThat(ApiUtils.validateMillisecondInstant(Instant.ofEpochMilli(123), "timestamp")).isEqualTo(123L);
        assertThatThrownBy(() -> ApiUtils.validateMillisecondInstant(Instant.MAX, "timestamp"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timestamp");
        PositionBound unbounded = PositionBound.unbounded();
        assertThat(unbounded.isUnbounded()).isTrue();
        assertThat(PositionBound.at(Position.emptyPosition().withComponent("topic", 0, 1L)).isUnbounded()).isFalse();
        StateQueryResult<String> result = new StateQueryResult<>();
        QueryResult<String> success = QueryResult.forResult("found");
        result.addResult(3, success);
        assertThat(result.getOnlyPartitionResult()).isSameAs(success);
    }

    @Test
    void taskAssignmentAndProcessorAdaptersExposeTheirPublicBehavior() {
        TaskId task = new TaskId(1, 2);
        ClientState state = new ClientState(Set.of(), Set.of(), Map.of(task, 0L), Map.of(), 1);
        state.assignStandby(task);
        state.assignStandbyToConsumer(task, "consumer");
        assertThat(state.assignedStandbyTasksByConsumer().get("consumer")).contains(task);
        TaskId activeTask = new TaskId(1, 3);
        state.assignActive(activeTask);
        state.assignActiveToConsumer(activeTask, "consumer");
        state.revokeActiveFromConsumer(activeTask, "consumer");
        assertThat(state.revokingActiveTasksByConsumer().get("consumer")).contains(activeTask);
        assertThat(new ClientState().capacity()).isZero();

        PendingUpdateAction close = PendingUpdateAction.createCloseClean();
        assertThat((Object) close.getAction()).isNotNull();
        ToInternal target = new ToInternal();
        target.update(To.child("downstream").withTimestamp(44L));
        assertThat(target.child()).isEqualTo("downstream");
        assertThat(target.hasTimestamp()).isTrue();
        assertThat(target.timestamp()).isEqualTo(44L);

        AtomicReference<Record<String, Integer>> seen = new AtomicReference<>();
        ForeachProcessor<String, Integer> processor = new ForeachProcessor<>((key, value) ->
                seen.set(new Record<>(key, value, 0L)));
        processor.process(new Record<>("order", 9, 10L));
        assertThat(seen.get().key()).isEqualTo("order");
        assertThat(seen.get().value()).isEqualTo(9);
    }

    @Test
    void exceptionAndProductionPoliciesPreserveDiagnosticInformation() {
        RuntimeException cause = new RuntimeException("root");
        List<RuntimeException> exceptions = List.of(
                new BrokerNotFoundException("message", cause), new BrokerNotFoundException(cause),
                new InvalidStateStoreException("message", cause), new InvalidStateStoreException(cause),
                new InvalidStateStorePartitionException("message"), new InvalidStateStorePartitionException("message", cause),
                new LockException("message", cause), new LockException(cause),
                new ProcessorStateException("message", cause), new ProcessorStateException(cause),
                new StateStoreMigratedException("message"), new StateStoreMigratedException("message", cause),
                new StateStoreNotAvailableException("message"), new StateStoreNotAvailableException("message", cause),
                new StreamsNotStartedException("message"), new StreamsNotStartedException("message", cause),
                new StreamsRebalancingException("message"), new StreamsRebalancingException("message", cause),
                new StreamsStoppedException("message"), new StreamsStoppedException("message", cause),
                new TaskAssignmentException("message", cause), new TaskAssignmentException(cause),
                new TaskIdFormatException("message", cause), new TaskIdFormatException(cause),
                new TaskMigratedException("message"), new TaskMigratedException("message", cause),
                new TopologyException("message", cause), new TopologyException(cause),
                new UnknownStateStoreException("message"), new UnknownStateStoreException("message", cause),
                new UnknownTopologyException("message", "topology"),
                new UnknownTopologyException("message", cause, "topology"),
                new StreamsException("message", cause));
        assertThat(exceptions).allSatisfy(exception -> assertThat(exception.getMessage()).isNotBlank());

        Set<TaskId> corrupted = Set.of(new TaskId(3, 4));
        OffsetOutOfRangeException invalid = new OffsetOutOfRangeException(
                Map.of(new TopicPartition("topic", 4), 2L));
        assertThat(new TaskCorruptedException(corrupted).corruptedTasks()).isEqualTo(corrupted);
        assertThat(new TaskCorruptedException(corrupted, invalid)).hasCause(invalid);
        DefaultProductionExceptionHandler handler = new DefaultProductionExceptionHandler();
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>("output", new byte[] {1}, new byte[] {2});
        assertThat(handler.handle(record, cause)).isEqualTo(ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL);
        assertThat(handler.handleSerializationException(record, cause))
                .isEqualTo(ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL);
    }

    @Test
    void topicPartitionMetadataRoundTripsItsProcessorState() {
        ProcessorMetadata metadata = new ProcessorMetadata();
        metadata.put("processed", 12L);
        metadata.setNeedsCommit(true);
        TopicPartitionMetadata original = new TopicPartitionMetadata(42L, metadata);
        TopicPartitionMetadata decoded = TopicPartitionMetadata.decode(original.encode());

        assertThat(decoded.partitionTime()).isEqualTo(42L);
        assertThat(decoded.processorMetadata().get("processed")).isEqualTo(12L);
        assertThat(decoded.processorMetadata().needsCommit()).isFalse();
        assertThat(decoded).isEqualTo(original).hasSameHashCodeAs(original);
    }

    @Test
    void enumAndCloseOptionFactoriesExposeExpectedChoices() {
        assertThat(KafkaStreams.State.values()).contains(KafkaStreams.State.CREATED, KafkaStreams.State.RUNNING);
        assertThat(KafkaStreams.State.valueOf("RUNNING").isRunningOrRebalancing()).isTrue();
        assertThat(KafkaStreams.State.PENDING_SHUTDOWN.hasStartedOrFinishedShuttingDown()).isTrue();
        KafkaStreams.CloseOptions options = new KafkaStreams.CloseOptions().timeout(Duration.ofMillis(5)).leaveGroup(true);
        assertThat(options).isNotNull();
        assertThat(Topology.AutoOffsetReset.valueOf("EARLIEST")).isEqualTo(Topology.AutoOffsetReset.EARLIEST);
        assertThat(Topology.AutoOffsetReset.values()).isNotEmpty();
        assertThat(Serdes.String()).isNotNull();
    }
}
