/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse;
import org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.kstream.internals.WindowedStreamPartitioner;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.TaskMetadata;
import org.apache.kafka.streams.processor.ThreadMetadata;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.UsePartitionTimeOnInvalidTimestamp;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.junit.Test;

public class ValueObjectsAndSerializationCoverageTest {
    @Test
    public void taskAndThreadMetadataExposeStableValues() {
        TopicPartition partition = new TopicPartition("topic", 2);
        TaskMetadata task = new TaskMetadata("1_2", Set.of(partition), Map.of(partition, 3L),
                Map.of(partition, 8L), Optional.of(42L));
        TaskMetadata copy = new TaskMetadata("1_2", Set.of(partition), Map.of(partition, 3L),
                Map.of(partition, 8L), Optional.of(42L));
        assertThat(task.taskId()).isEqualTo("1_2");
        assertThat(task.topicPartitions()).containsExactly(partition);
        assertThat(task.committedOffsets()).containsEntry(partition, 3L);
        assertThat(task.endOffsets()).containsEntry(partition, 8L);
        assertThat(task.timeCurrentIdlingStarted()).contains(42L);
        assertThat(task).isEqualTo(copy).hasSameHashCodeAs(copy);
        assertThat(task.toString()).contains("1_2", "topic");

        ThreadMetadata thread = new ThreadMetadata("worker", "RUNNING", "consumer", "restore",
                Set.of("producer"), "admin", Set.of(task), Set.of());
        ThreadMetadata same = new ThreadMetadata("worker", "RUNNING", "consumer", "restore",
                Set.of("producer"), "admin", Set.of(copy), Set.of());
        assertThat(thread.threadState()).isEqualTo("RUNNING");
        assertThat(thread.threadName()).isEqualTo("worker");
        assertThat(thread.consumerClientId()).isEqualTo("consumer");
        assertThat(thread.restoreConsumerClientId()).isEqualTo("restore");
        assertThat(thread.producerClientIds()).containsExactly("producer");
        assertThat(thread.adminClientId()).isEqualTo("admin");
        assertThat(thread.activeTasks()).containsExactly(task);
        assertThat(thread.standbyTasks()).isEmpty();
        assertThat(thread).isEqualTo(same);
        assertThat(thread.toString()).contains("worker", "RUNNING");
    }

    @Test
    public void taskIdsRoundTripAndOrderingIsPreserved() throws Exception {
        TaskId original = new TaskId(3, 7, "orders");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            original.writeTo(output, 11);
        }
        TaskId decoded;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = TaskId.readFrom(input, 11);
        }
        assertThat(decoded).isEqualTo(original);
        assertThat(((Comparable<Object>) (Comparable<?>) original).compareTo(new TaskId(3, 8, "orders"))).isNegative();
    }

    @Test
    public void windowedSerdesRoundTripAndPartitionByBaseKey() {
        Windowed<String> value = new Windowed<>("customer", new TimeWindow(10L, 20L));
        TimeWindowedSerializer<String> serializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> deserializer =
                new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 10L);
        org.apache.kafka.common.serialization.Serializer rawSerializer = serializer;
        org.apache.kafka.common.serialization.Deserializer rawDeserializer = deserializer;
        byte[] bytes = rawSerializer.serialize("topic", value);
        assertThat((Windowed<String>) rawDeserializer.deserialize("topic", bytes)).isEqualTo(value);
        WindowedStreamPartitioner<String, String> partitioner = new WindowedStreamPartitioner<>(serializer);
        org.apache.kafka.streams.processor.StreamPartitioner rawPartitioner = partitioner;
        assertThat(partitioner.partition("topic", value, "payload", 17))
                .isEqualTo(rawPartitioner.partition("topic", value, "payload", 17));

        SessionWindowedSerializer<String> sessionSerializer =
                new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> sessionDeserializer =
                new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        Windowed<String> session = new Windowed<>("customer", new SessionWindow(4L, 9L));
        org.apache.kafka.common.serialization.Serializer rawSessionSerializer = sessionSerializer;
        org.apache.kafka.common.serialization.Deserializer rawSessionDeserializer = sessionDeserializer;
        assertThat((Windowed<String>) rawSessionDeserializer.deserialize("topic",
                rawSessionSerializer.serialize("topic", session))).isEqualTo(session);
    }

    @Test
    public void timestampPoliciesHandleValidAndInvalidRecords() {
        ConsumerRecord<Object, Object> valid = new ConsumerRecord<>("topic", 0, 0L, 123L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "value",
                new RecordHeaders(), Optional.empty());
        ConsumerRecord<Object, Object> invalid = new ConsumerRecord<>("topic", 0, 0L, -1L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "value",
                new RecordHeaders(), Optional.empty());
        assertThat(new FailOnInvalidTimestamp().extract(valid, 9L)).isEqualTo(123L);
        assertThatThrownBy(() -> new FailOnInvalidTimestamp().extract(invalid, 9L)).isInstanceOf(StreamsException.class);
        assertThat(new LogAndSkipOnInvalidTimestamp().extract(invalid, 9L)).isEqualTo(-1L);
        assertThat(new UsePartitionTimeOnInvalidTimestamp().extract(invalid, 9L)).isEqualTo(9L);
        assertThat(new WallclockTimestampExtractor().extract(valid, 9L)).isPositive();
    }

    @Test
    public void enumsAndForwardingOptionsHonorNamesAndValues() {
        assertThat(KafkaStreams.State.valueOf("RUNNING")).isIn(KafkaStreams.State.values());
        assertThat(Topology.AutoOffsetReset.valueOf("EARLIEST")).isIn(Topology.AutoOffsetReset.values());
        assertThat(DeserializationHandlerResponse.valueOf("CONTINUE")).isIn(DeserializationHandlerResponse.values());
        assertThat(ProductionExceptionHandlerResponse.valueOf("FAIL")).isIn(ProductionExceptionHandlerResponse.values());
        assertThat(StreamThreadExceptionResponse.valueOf("SHUTDOWN_CLIENT")).isIn(StreamThreadExceptionResponse.values());
        assertThat(PunctuationType.valueOf("STREAM_TIME")).isIn(PunctuationType.values());
        To first = To.child("sink").withTimestamp(9L);
        To second = To.child("sink").withTimestamp(9L);
        assertThat(first).isEqualTo(second);
        assertThatThrownBy(first::hashCode).isInstanceOf(UnsupportedOperationException.class);
        assertThat(first.toString()).contains("sink", "9");
    }
}
