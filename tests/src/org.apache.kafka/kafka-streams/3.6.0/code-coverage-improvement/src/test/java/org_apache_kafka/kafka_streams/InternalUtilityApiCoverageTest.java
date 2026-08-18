/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.errors.DefaultProductionExceptionHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.internals.PendingUpdateAction;
import org.apache.kafka.streams.processor.internals.ProcessorMetadata;
import org.apache.kafka.streams.processor.internals.ToInternal;
import org.apache.kafka.streams.processor.internals.TopicPartitionMetadata;
import org.apache.kafka.streams.query.Position;
import org.apache.kafka.streams.query.PositionBound;
import org.apache.kafka.streams.query.QueryResult;
import org.apache.kafka.streams.query.StateQueryResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises public utility state machines and error handlers with observable results. */
public class InternalUtilityApiCoverageTest {
    @Test
    void shouldUpdateForwardingAndPendingActions() {
        ToInternal forwarding = new ToInternal();
        forwarding.update(To.child("child").withTimestamp(17L));
        assertThat(forwarding.child()).isEqualTo("child");
        assertThat(forwarding.timestamp()).isEqualTo(17L);
        assertThat(forwarding.hasTimestamp()).isTrue();
        ToInternal copied = new ToInternal(To.all().withTimestamp(19L));
        assertThat(copied.hasTimestamp()).isTrue();
        assertThat(copied.timestamp()).isEqualTo(19L);

        TopicPartition partition = new TopicPartition("input", 0);
        assertThat(PendingUpdateAction.createCloseClean()).isNotNull();
        assertThat(PendingUpdateAction.createCloseDirty()).isNotNull();
        assertThat(PendingUpdateAction.createSuspend()).isNotNull();
        assertThat(PendingUpdateAction.createRecycleTask(Set.of(partition)).getInputPartitions()).contains(partition);
        assertThat(PendingUpdateAction.createUpdateInputPartition(Set.of(partition)).getInputPartitions()).contains(partition);
    }

    @Test
    void shouldEncodeProcessorMetadataAndTopicPartitionState() {
        ProcessorMetadata processorMetadata = new ProcessorMetadata(new HashMap<>(Map.of("store", 8L)));
        processorMetadata.put("other", 9L);
        processorMetadata.setNeedsCommit(true);
        assertThat(processorMetadata.get("store")).isEqualTo(8L);
        assertThat(processorMetadata.needsCommit()).isTrue();
        assertThat(ProcessorMetadata.deserialize(processorMetadata.serialize())).isEqualTo(processorMetadata);

        TopicPartitionMetadata metadata = new TopicPartitionMetadata(123L, processorMetadata);
        TopicPartitionMetadata decoded = TopicPartitionMetadata.decode(metadata.encode());
        assertThat(decoded.partitionTime()).isEqualTo(123L);
        assertThat(decoded.processorMetadata()).isEqualTo(processorMetadata);
        assertThat(decoded).isEqualTo(metadata);
        assertThat(decoded.hashCode()).isEqualTo(metadata.hashCode());
    }

    @Test
    void shouldReportQueryResultsAndPositionBounds() {
        Position position = Position.emptyPosition().withComponent("topic", 0, 4L);
        assertThat(PositionBound.unbounded().isUnbounded()).isTrue();
        assertThat(PositionBound.at(position).isUnbounded()).isFalse();
        assertThat(PositionBound.at(position).position()).isEqualTo(position);

        StateQueryResult<String> queryResult = new StateQueryResult<>();
        QueryResult<String> first = QueryResult.forResult("first");
        queryResult.addResult(0, first);
        assertThat(queryResult.getOnlyPartitionResult()).isSameAs(first);
        assertThat(queryResult.getPartitionResults()).containsEntry(0, first);
        queryResult.setGlobalResult(QueryResult.forResult("global"));
        assertThat(queryResult.getGlobalResult().getResult()).isEqualTo("global");
        assertThat(queryResult.toString()).contains("first");
    }

    @Test
    void shouldReturnDocumentedHandlerResponses() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 0, 1L, new byte[] {1}, new byte[] {2});
        Exception failure = new IllegalArgumentException("bad input");
        LogAndContinueExceptionHandler continueHandler = new LogAndContinueExceptionHandler();
        continueHandler.configure(Map.of());
        org.apache.kafka.streams.processor.ProcessorContext context = (org.apache.kafka.streams.processor.ProcessorContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {org.apache.kafka.streams.processor.ProcessorContext.class},
                (proxy, method, args) -> method.getName().equals("taskId") ? new org.apache.kafka.streams.processor.TaskId(0, 0) : null);
        assertThat(continueHandler.handle(context, record, failure))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE);
        LogAndFailExceptionHandler failHandler = new LogAndFailExceptionHandler();
        failHandler.configure(Map.of());
        assertThat(failHandler.handle(context, record, failure))
                .isEqualTo(DeserializationExceptionHandler.DeserializationHandlerResponse.FAIL);
        DefaultProductionExceptionHandler productionHandler = new DefaultProductionExceptionHandler();
        productionHandler.configure(Map.of());
        assertThat(productionHandler.handle(new ProducerRecord<>("topic", new byte[] {1}, new byte[] {2}), failure))
                .isEqualTo(org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL);
    }
}
