/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.BrokerNotFoundException;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.LockException;
import org.apache.kafka.streams.errors.ProcessorStateException;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.TaskAssignmentException;
import org.apache.kafka.streams.errors.TaskIdFormatException;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicValueTypesCoverageTest {
    @Test
    void queryParametersAndMetadataHaveStableValueSemantics() {
        StoreQueryParameters<?> parameters = StoreQueryParameters
                .fromNameAndType("orders", QueryableStoreTypes.keyValueStore())
                .withPartition(2).enableStaleStores();
        assertThat(parameters.storeName()).isEqualTo("orders");
        assertThat(parameters.partition()).isEqualTo(2);
        assertThat(parameters.staleStoresEnabled()).isTrue();
        assertThat(parameters.queryableStoreType()).isNotNull();
        assertThat(parameters).isEqualTo(parameters).hasSameHashCodeAs(parameters);
        assertThat(parameters.toString()).contains("orders");
        assertThat(QueryableStoreTypes.sessionStore()).isNotNull();
        assertThat(QueryableStoreTypes.windowStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedKeyValueStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedWindowStore()).isNotNull();

        HostInfo active = new HostInfo("active", 8080);
        Set<HostInfo> standby = Set.of(new HostInfo("standby", 8081));
        KeyQueryMetadata metadata = new KeyQueryMetadata(active, standby, 3);
        assertThat(metadata.activeHost()).isEqualTo(active);
        assertThat(metadata.getActiveHost()).isEqualTo(active);
        assertThat(metadata.standbyHosts()).isEqualTo(standby);
        assertThat(metadata.getStandbyHosts()).isEqualTo(standby);
        assertThat(metadata.partition()).isEqualTo(3);
        assertThat(metadata.getPartition()).isEqualTo(3);
        assertThat(metadata).isEqualTo(new KeyQueryMetadata(active, standby, 3));
        assertThat(metadata.hashCode()).isEqualTo(new KeyQueryMetadata(active, standby, 3).hashCode());
        assertThat(metadata.toString()).contains("active");
    }

    @Test
    void taskIdsRoundTripAndValueObjectsCompareByContent() {
        TaskId original = new TaskId(7, 11);
        assertThat(TaskId.parse("7_11")).isEqualTo(original);
        assertThat(original.compareTo(new TaskId(7, 12))).isNegative();
        assertThat(((Comparable<Object>) (Comparable<?>) original).compareTo(new TaskId(7, 11))).isZero();
        ByteBuffer buffer = ByteBuffer.allocate(64);
        original.writeTo(buffer, 1);
        buffer.flip();
        assertThat(TaskId.readFrom(buffer, 1)).isEqualTo(original);

        KeyValue<String, Integer> pair = KeyValue.pair("count", 2);
        assertThat(pair).isEqualTo(new KeyValue<>("count", 2));
        assertThat(pair.hashCode()).isEqualTo(new KeyValue<>("count", 2).hashCode());
        assertThat(pair.toString()).contains("count", "2");
        Windowed<String> windowed = new Windowed<>("key",
                new org.apache.kafka.streams.kstream.internals.TimeWindow(0, 10));
        assertThat(windowed.hashCode()).isEqualTo(new Windowed<>("key",
                new org.apache.kafka.streams.kstream.internals.TimeWindow(0, 10)).hashCode());
    }

    @Test
    void streamExceptionsPreserveMessageCauseAndTaskContext() {
        Throwable cause = new IllegalArgumentException("bad input");
        TaskId taskId = new TaskId(1, 2);
        StreamsException exception = new StreamsException("failed", cause, taskId);
        assertThat(exception).hasMessage("failed").hasCause(cause);
        assertThat(exception.taskId()).contains(taskId);
        exception.setTaskId(new TaskId(3, 4));
        assertThat(exception.taskId()).contains(new TaskId(3, 4));
        assertThat(new StreamsException("message")).hasMessage("message");
        assertThat(new StreamsException(cause)).hasCause(cause);
        assertThat(new StreamsException(cause, taskId).taskId()).contains(taskId);
        assertThat(new StreamsException("message", taskId).taskId()).contains(taskId);

        assertException(new BrokerNotFoundException("broker"), "broker");
        assertException(new InvalidStateStoreException("store"), "store");
        assertException(new LockException("lock"), "lock");
        assertException(new ProcessorStateException("processor"), "processor");
        assertException(new TaskAssignmentException("assignment"), "assignment");
        assertThat(new TaskIdFormatException("task id")).hasMessageContaining("task id");
    }

    private static void assertException(RuntimeException exception, String message) {
        assertThat(exception).hasMessage(message);
    }
}
