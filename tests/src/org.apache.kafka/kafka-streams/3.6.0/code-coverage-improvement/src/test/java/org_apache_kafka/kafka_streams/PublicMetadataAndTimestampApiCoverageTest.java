/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.TaskMetadata;
import org.apache.kafka.streams.processor.ThreadMetadata;
import org.apache.kafka.streams.processor.To;
import org.apache.kafka.streams.processor.UsePartitionTimeOnInvalidTimestamp;
import org.apache.kafka.streams.processor.internals.TaskMetadataImpl;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises public task metadata, timestamp policy, and forwarding value contracts. */
public class PublicMetadataAndTimestampApiCoverageTest {
    @Test
    void shouldExposeTaskAndThreadMetadataConsistently() {
        TopicPartition partition = new TopicPartition("orders", 2);
        TaskMetadata task = new TaskMetadata("0_2", Set.of(partition), Map.of(partition, 11L),
                Map.of(partition, 20L), Optional.of(77L));
        TaskMetadata sameTask = new TaskMetadata("0_2", Set.of(partition), Map.of(partition, 11L),
                Map.of(partition, 20L), Optional.of(77L));

        assertThat(task.taskId()).isEqualTo("0_2");
        assertThat(task.topicPartitions()).containsExactly(partition);
        assertThat(task.committedOffsets()).containsEntry(partition, 11L);
        assertThat(task.endOffsets()).containsEntry(partition, 20L);
        assertThat(task.timeCurrentIdlingStarted()).contains(77L);
        assertThat(task).isEqualTo(sameTask);
        assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());
        assertThat(task.toString()).contains("0_2", "orders");

        ThreadMetadata thread = new ThreadMetadata("stream-thread-1", "RUNNING", "consumer-1",
                "restore-1", Set.of("producer-1"), "admin-1", Set.of(task), Set.of(sameTask));
        assertThat(thread.threadState()).isEqualTo("RUNNING");
        assertThat(thread.threadName()).isEqualTo("stream-thread-1");
        assertThat(thread.consumerClientId()).isEqualTo("consumer-1");
        assertThat(thread.restoreConsumerClientId()).isEqualTo("restore-1");
        assertThat(thread.producerClientIds()).containsExactly("producer-1");
        assertThat(thread.adminClientId()).isEqualTo("admin-1");
        assertThat(thread.activeTasks()).contains(task);
        assertThat(thread.standbyTasks()).contains(sameTask);
        assertThat(thread).isEqualTo(new ThreadMetadata("stream-thread-1", "RUNNING", "consumer-1",
                "restore-1", Set.of("producer-1"), "admin-1", Set.of(sameTask), Set.of(task)));
        assertThat(thread.hashCode()).isNotZero();
        assertThat(thread.toString()).contains("stream-thread-1", "RUNNING");
    }

    @Test
    void shouldExposeInternalTaskMetadataAndLagCalculations() {
        TopicPartition partition = new TopicPartition("payments", 1);
        org.apache.kafka.streams.processor.TaskId taskId = new org.apache.kafka.streams.processor.TaskId(3, 1);
        TaskMetadataImpl task = new TaskMetadataImpl(taskId, Set.of(partition),
                Map.of(partition, 8L), Map.of(partition, 13L), Optional.of(55L));
        TaskMetadataImpl same = new TaskMetadataImpl(taskId, Set.of(partition),
                Map.of(partition, 8L), Map.of(partition, 13L), Optional.of(55L));
        assertThat(task.taskId()).isEqualTo(taskId);
        assertThat(task.topicPartitions()).containsExactly(partition);
        assertThat(task.committedOffsets()).containsEntry(partition, 8L);
        assertThat(task.endOffsets()).containsEntry(partition, 13L);
        assertThat(task.timeCurrentIdlingStarted()).contains(55L);
        assertThat(task).isEqualTo(same);
        assertThat(task.hashCode()).isEqualTo(same.hashCode());
        assertThat(task.toString()).contains("3_1", "payments");
    }

    @Test
    void shouldRoundTripTaskIdsAndForwardingDestinations() throws Exception {
        TaskId taskId = new TaskId(4, 7);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        taskId.writeTo(new DataOutputStream(bytes), 0);
        TaskId decoded = TaskId.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 0);
        assertThat(decoded).isEqualTo(taskId);
        assertThat(TaskId.parse(taskId.toString())).isEqualTo(taskId);
        assertThat(taskId.compareTo(new TaskId(5, 0))).isNegative();

        To child = To.child("orders-child").withTimestamp(19L);
        To sameChild = To.child("orders-child").withTimestamp(19L);
        assertThat(child).isEqualTo(sameChild);
        assertThatThrownBy(child::hashCode).isInstanceOf(UnsupportedOperationException.class);
        assertThat(child.toString()).contains("orders-child", "19");
        assertThat(To.all()).isNotEqualTo(child);
    }

    @Test
    void shouldApplyTimestampPoliciesToValidAndLegacyRecords() {
        ConsumerRecord<Object, Object> valid = new ConsumerRecord<>("orders", 0, 3L, 42L,
                TimestampType.CREATE_TIME, -1, -1, -1, "key", "value");
        ConsumerRecord<Object, Object> invalid = new ConsumerRecord<>("orders", 0, 4L, "key", "value");

        FailOnInvalidTimestamp fail = new FailOnInvalidTimestamp();
        assertThat(fail.extract(valid, 8L)).isEqualTo(42L);
        assertThatThrownBy(() -> fail.extract(invalid, 8L)).isInstanceOf(StreamsException.class);
        assertThatThrownBy(() -> fail.onInvalidTimestamp(invalid, -1L, 8L)).isInstanceOf(StreamsException.class);

        LogAndSkipOnInvalidTimestamp skip = new LogAndSkipOnInvalidTimestamp();
        assertThat(skip.extract(valid, 8L)).isEqualTo(42L);
        assertThat(skip.extract(invalid, 8L)).isEqualTo(-1L);
        assertThat(skip.onInvalidTimestamp(invalid, -1L, 8L)).isEqualTo(-1L);

        UsePartitionTimeOnInvalidTimestamp partitionTime = new UsePartitionTimeOnInvalidTimestamp();
        assertThat(partitionTime.extract(valid, 8L)).isEqualTo(42L);
        assertThat(partitionTime.extract(invalid, 123L)).isEqualTo(123L);
        assertThatThrownBy(() -> partitionTime.onInvalidTimestamp(invalid, -1L, -1L))
                .isInstanceOf(StreamsException.class);

        long before = System.currentTimeMillis();
        long wallclock = new WallclockTimestampExtractor().extract(valid, 8L);
        assertThat(wallclock).isBetween(before, System.currentTimeMillis());
    }
}
