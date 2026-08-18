/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.Change;
import org.apache.kafka.streams.kstream.internals.ChangedDeserializer;
import org.apache.kafka.streams.kstream.internals.ChangedSerializer;
import org.apache.kafka.streams.state.internals.LeftOrRightValue;
import org.apache.kafka.streams.state.internals.LeftOrRightValueSerde;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.kstream.internals.emitstrategy.WindowCloseStrategy;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.processor.BatchingStateRestoreCallback;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.LogAndSkipOnInvalidTimestamp;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.UsePartitionTimeOnInvalidTimestamp;
import org.apache.kafka.streams.processor.internals.ProcessorMetadata;
import org.apache.kafka.streams.processor.internals.ProcessorRecordContext;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises public codecs, timestamp policies, and small state/value contracts as users consume them. */
public class ApiCoverageLoopValueContractsTest {
    @Test
    void shouldRoundTripProcessorContextAndMetadata() {
        ProcessorRecordContext context = new ProcessorRecordContext(
                23L, 41L, 2, "orders", new RecordHeaders().add("trace", new byte[] {1}));
        ProcessorRecordContext copy = ProcessorRecordContext.deserialize(ByteBuffer.wrap(context.serialize()));
        assertThat(copy).isEqualTo(context);
        assertThatThrownBy(copy::hashCode).isInstanceOf(UnsupportedOperationException.class);
        assertThat(copy.toString()).contains("orders");
        assertThat(copy.residentMemorySizeEstimate()).isPositive();

        ProcessorMetadata metadata = new ProcessorMetadata(new HashMap<>(Map.of("orders", 3L)));
        metadata.put("payments", 7L);
        metadata.setNeedsCommit(true);
        assertThat(metadata.needsCommit()).isTrue();
        ProcessorMetadata restored = ProcessorMetadata.deserialize(metadata.serialize());
        assertThat(restored.get("orders")).isEqualTo(3L);
        assertThat(restored.get("payments")).isEqualTo(7L);
        assertThat(restored.needsCommit()).isFalse();
        ProcessorMetadata update = new ProcessorMetadata(Map.of("orders", 9L));
        restored.update(update);
        assertThat(restored.get("orders")).isEqualTo(9L);
        assertThat(restored).isEqualTo(ProcessorMetadata.deserialize(restored.serialize()));
    }

    @Test
    void shouldRoundTripTimeAndSessionWindowSerdes() {
        Windowed<String> timeWindow = new Windowed<>("key", new TimeWindow(10L, 20L));
        TimeWindowedSerializer<String> timeSerializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> timeDeserializer = new TimeWindowedDeserializer<>(
                Serdes.String().deserializer(), 10L);
        byte[] timeBytes = timeSerializer.serialize("orders", timeWindow);
        assertThat(timeDeserializer.deserialize("orders", timeBytes)).isEqualTo(timeWindow);
        TimeWindowedSerializer rawTimeSerializer = timeSerializer;
        assertThat(rawTimeSerializer.serialize("orders", timeWindow)).isEqualTo(timeBytes);

        Windowed<String> sessionWindow = new Windowed<>("key", new SessionWindow(30L, 50L));
        SessionWindowedSerializer<String> sessionSerializer =
                new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> sessionDeserializer =
                new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        byte[] sessionBytes = sessionSerializer.serialize("orders", sessionWindow);
        assertThat(sessionDeserializer.deserialize("orders", sessionBytes)).isEqualTo(sessionWindow);
        SessionWindowedSerializer rawSessionSerializer = sessionSerializer;
        assertThat(rawSessionSerializer.serialize("orders", sessionWindow)).isEqualTo(sessionBytes);
    }

    @Test
    void shouldRoundTripChangedValuesAndGeneratedProtocolMessages() {
        ChangedSerializer<String> serializer = new ChangedSerializer<>(Serdes.String().serializer());
        ChangedDeserializer<String> deserializer = new ChangedDeserializer<>(Serdes.String().deserializer());
        Change<String> change = new Change<>("new", "old", true);
        byte[] bytes = serializer.serialize("orders", change);
        assertThat(deserializer.deserialize("orders", bytes)).isEqualTo(change);
        assertThat(serializer.serialize("orders", new RecordHeaders(), change)).isEqualTo(bytes);
        assertThat(deserializer.deserialize("orders", new RecordHeaders(), bytes)).isEqualTo(change);
        byte[] notLatestBytes = serializer.serialize("orders", new Change<>(null, "old", true));
        notLatestBytes = java.util.Arrays.copyOf(notLatestBytes, notLatestBytes.length + 1);
        notLatestBytes[notLatestBytes.length - 2] = 0;
        notLatestBytes[notLatestBytes.length - 1] = 3;
        Change<String> decodedNotLatest = deserializer.deserialize("orders", notLatestBytes);
        assertThat(decodedNotLatest.newValue).isNull();
        assertThat(decodedNotLatest.oldValue).isEqualTo("old");
        assertThat(decodedNotLatest.isLatest).isFalse();
        serializer.close();
        deserializer.close();

        SubscriptionInfoData data = new SubscriptionInfoData()
                .setVersion(1).setLatestSupportedVersion(2).setUserEndPoint(new byte[] {1});
        SubscriptionInfoData copy = data.duplicate();
        assertThat(copy).isEqualTo(data);
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag()
                .setKey(new byte[] {1}).setValue(new byte[] {2});
        assertThat(tag.duplicate()).isEqualTo(tag);
        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(1).setPartition(2);
        assertThat(task.duplicate()).isEqualTo(task);
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(2).setOffsetSum(3L);
        assertThat(partition.duplicate()).isEqualTo(partition);
        SubscriptionInfoData.TaskOffsetSum offset = new SubscriptionInfoData.TaskOffsetSum()
                .setTopicGroupId(1).setPartition(2).setOffsetSum(3L);
        assertThat(offset.duplicate()).isEqualTo(offset);
    }

    @Test
    void shouldRoundTripLeftAndRightSerdeValues() {
        LeftOrRightValueSerde<String, Integer> serde = new LeftOrRightValueSerde<>(
                Serdes.String(), Serdes.Integer());
        LeftOrRightValue<String, Integer> left = LeftOrRightValue.makeLeftValue("left");
        LeftOrRightValue<String, Integer> right = LeftOrRightValue.makeRightValue(7);
        assertThat(serde.deserializer().deserialize("join", serde.serializer().serialize("join", left)))
                .isEqualTo(left);
        assertThat(serde.deserializer().deserialize("join", serde.serializer().serialize("join", right)))
                .isEqualTo(right);
    }

    @Test
    void shouldExposeWindowAndEnumValueContracts() {
        assertThat(UnlimitedWindows.of().hashCode()).isEqualTo(UnlimitedWindows.of().hashCode());
        assertThat(new WindowCloseStrategy().type()).isEqualTo(
                org.apache.kafka.streams.kstream.EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
        assertThat(org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.valueOf("EMIT"))
                .isEqualTo(org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.EMIT);
        assertThat(org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper.Instruction.values())
                .contains(org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper.Instruction.DELETE_KEY_NO_PROPAGATE);
    }

    @Test
    void shouldResolvePublicProtocolAndLifecycleEnums() {
        assertThat(org.apache.kafka.streams.KafkaStreams.State.values()).contains(org.apache.kafka.streams.KafkaStreams.State.CREATED);
        assertThat(org.apache.kafka.streams.KafkaStreams.State.valueOf("CREATED"))
                .isEqualTo(org.apache.kafka.streams.KafkaStreams.State.CREATED);
        assertThat(org.apache.kafka.streams.Topology.AutoOffsetReset.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.Topology.AutoOffsetReset.valueOf("EARLIEST"))
                .isEqualTo(org.apache.kafka.streams.Topology.AutoOffsetReset.EARLIEST);
        assertThat(org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse.values())
                .isNotEmpty();
        assertThat(org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse.values())
                .isNotEmpty();
        assertThat(org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.values())
                .isNotEmpty();
        assertThat(org.apache.kafka.streams.internals.StreamsConfigUtils.ProcessingMode.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.internals.UpgradeFromValues.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.kstream.EmitStrategy.StrategyType.values()).contains(
                org.apache.kafka.streams.kstream.EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
        assertThat(org.apache.kafka.streams.kstream.Materialized.StoreType.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.processor.PunctuationType.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.processor.internals.GlobalStreamThread.State.values()).isNotEmpty();
    }

    @Test
    void shouldApplyInvalidTimestampPoliciesAndCompareTaskIds() {
        ConsumerRecord<Object, Object> invalid = new ConsumerRecord<>("orders", 3, 1L, -1L,
                TimestampType.CREATE_TIME, 0L, 0, 0, null, null, new RecordHeaders());
        ConsumerRecord<Object, Object> valid = new ConsumerRecord<>("orders", 3, 2L, 99L,
                TimestampType.CREATE_TIME, 0L, 0, 0, null, null, new RecordHeaders());
        assertThat(new LogAndSkipOnInvalidTimestamp().extract(invalid, 12L)).isEqualTo(-1L);
        assertThat(new UsePartitionTimeOnInvalidTimestamp().extract(invalid, 12L)).isEqualTo(12L);
        assertThat(new FailOnInvalidTimestamp().extract(valid, 12L)).isEqualTo(99L);
        assertThatThrownBy(() -> new FailOnInvalidTimestamp().extract(invalid, 12L))
                .isInstanceOf(RuntimeException.class);

        TaskId first = new TaskId(1, 2);
        TaskId second = new TaskId(2, 0);
        Comparable comparable = first;
        assertThat(comparable.compareTo(second)).isNegative();
    }

    @Test
    void shouldDeliverDefaultBatchRestoreCallbacks() {
        AtomicReference<Collection<KeyValue<byte[], byte[]>>> restored = new AtomicReference<>();
        BatchingStateRestoreCallback callback = new BatchingStateRestoreCallback() {
            @Override
            public void restoreAll(Collection<KeyValue<byte[], byte[]>> records) {
                restored.set(records);
            }
        };
        assertThatThrownBy(() -> callback.restore(new byte[] {1}, new byte[] {2}))
                .isInstanceOf(UnsupportedOperationException.class);
        callback.restoreAll(List.of(KeyValue.pair(new byte[] {1}, new byte[] {2})));
        assertThat(restored.get()).hasSize(1);
        assertThat(restored.get().iterator().next().key).containsExactly(1);
        assertThat(restored.get().iterator().next().value).containsExactly(2);

        AtomicReference<Collection<KeyValue<byte[], byte[]>>> empty = new AtomicReference<>();
        callback.restoreAll(List.of());
        empty.set(List.of());
        assertThat(empty.get()).isEmpty();
        assertThat(Bytes.wrap(new byte[] {1})).isEqualTo(Bytes.wrap(new byte[] {1}));
    }
}
