/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.MessageSizeAccumulator;
import org.apache.kafka.common.protocol.MessageUtil;
import org.apache.kafka.common.protocol.ObjectSerializationCache;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.Stores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaStreamsWindowAndGeneratedApiCoverageTest {

    @Test
    void roundTripsTimeWindowedKeysAndExposesWindowDefinitions() {
        Windowed<String> order = new Windowed<>("order-7", new TimeWindow(100L, 200L));
        TimeWindowedSerializer<String> serializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> deserializer = new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 100L);
        serializer.configure(Map.of(), true);
        deserializer.configure(Map.of(), true);

        byte[] encoded = serializer.serialize("orders", order);
        assertThat(serializer.serializeBaseKey("orders", order)).isNotEmpty();
        assertThat(deserializer.deserialize("orders", encoded)).isEqualTo(order);
        assertThat(deserializer.getWindowSize()).isEqualTo(100L);
        assertThat(order.hashCode()).isEqualTo(new Windowed<>("order-7", new TimeWindow(100L, 200L)).hashCode());
        assertThat(order.toString()).contains("order-7").contains("100");
        serializer.close();
        deserializer.close();
        TimeWindowedSerializer<String> defaultSerializer = new TimeWindowedSerializer<>();
        TimeWindowedDeserializer<String> defaultDeserializer = new TimeWindowedDeserializer<>();
        defaultDeserializer.setIsChangelogTopic(true);
        assertThat(defaultSerializer).isNotNull();
        assertThat(defaultDeserializer).isNotNull();

        TimeWindows windows = TimeWindows.ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(2))
                .advanceBy(Duration.ofSeconds(5));
        assertThat(windows.size()).isEqualTo(10_000L);
        assertThat(windows.gracePeriodMs()).isEqualTo(2_000L);
        assertThat(windows.windowsFor(12_000L)).hasSize(2);
        assertThat(windows).isEqualTo(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(10), Duration.ofSeconds(2))
                .advanceBy(Duration.ofSeconds(5)));
        assertThat(windows.toString()).contains("TimeWindows");
        assertThat(TimeWindows.of(Duration.ofSeconds(10)).grace(Duration.ofSeconds(1))).isNotNull();
        assertThat(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10))).isNotNull();

        UnlimitedWindows unlimited = UnlimitedWindows.of().startOn(Instant.ofEpochMilli(50L));
        assertThat(unlimited.windowsFor(100L)).hasSize(1);
        assertThat(unlimited.size()).isEqualTo(Long.MAX_VALUE);
        assertThat(unlimited.gracePeriodMs()).isEqualTo(0L);
        assertThat(unlimited).isEqualTo(UnlimitedWindows.of().startOn(Instant.ofEpochMilli(50L)));
        assertThat(unlimited.toString()).contains("UnlimitedWindows");

        assertThat(order.window().startTime()).isEqualTo(Instant.ofEpochMilli(100L));
        assertThat(order.window().endTime()).isEqualTo(Instant.ofEpochMilli(200L));
        assertThat(order.window().toString()).contains("startMs=100");
        assertThat(new WindowedSerdes()).isNotNull();
        Serde<Windowed<String>> windowedSerde = WindowedSerdes.timeWindowedSerdeFrom(String.class, 100L);
        assertThat(windowedSerde.deserializer().deserialize("orders", windowedSerde.serializer().serialize("orders", order)))
                .isEqualTo(order);
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class)).isNotNull();
        assertThat(WindowedSerdes.sessionWindowedSerdeFrom(String.class)).isNotNull();
    }

    @Test
    void preservesGeneratedSubscriptionPayloadFieldsAndProtocolCapabilities() {
        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(3).setPartition(9);
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(9).setOffsetSum(44L);
        SubscriptionInfoData.TaskOffsetSum offset = new SubscriptionInfoData.TaskOffsetSum().setTopicGroupId(3)
                .setPartition(9).setOffsetSum(44L).setNamedTopology("payments")
                .setPartitionToOffsetSum(List.of(partition));
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag().setKey(new byte[] {7})
                .setValue(new byte[] {8, 9});
        SubscriptionInfoData data = new SubscriptionInfoData().setVersion(1).setLatestSupportedVersion(11)
                .setUniqueField((byte) 2).setPrevTasks(List.of(task)).setStandbyTasks(List.of(task))
                .setTaskOffsetSums(List.of(offset)).setClientTags(List.of(tag));

        assertThat(data.apiKey()).isNotZero();
        assertThat(data.lowestSupportedVersion()).isLessThanOrEqualTo(data.highestSupportedVersion());
        assertThat(data.uniqueField()).isEqualTo((byte) 2);
        assertThat(data.unknownTaggedFields()).isEmpty();
        assertThat(data.hashCode()).isEqualTo(data.duplicate().hashCode());
        assertThat(task.topicGroupId()).isEqualTo(3);
        assertThat(task.partition()).isEqualTo(9);
        assertThat(task.lowestSupportedVersion()).isLessThanOrEqualTo(task.highestSupportedVersion());
        assertThat(task.unknownTaggedFields()).isEmpty();
        assertThat(task.hashCode()).isEqualTo(task.duplicate().hashCode());
        assertThat(offset.topicGroupId()).isEqualTo(3);
        assertThat(offset.partition()).isEqualTo(9);
        assertThat(offset.offsetSum()).isEqualTo(44L);
        assertThat(offset.namedTopology()).isEqualTo("payments");
        assertThat(offset.unknownTaggedFields()).isEmpty();
        assertThat(offset.hashCode()).isEqualTo(offset.duplicate().hashCode());
        assertThat(partition.partition()).isEqualTo(9);
        assertThat(partition.offsetSum()).isEqualTo(44L);
        assertThat(partition.unknownTaggedFields()).isEmpty();
        assertThat(partition.hashCode()).isEqualTo(partition.duplicate().hashCode());
        assertThat(tag.unknownTaggedFields()).isEmpty();
        assertThat(tag.hashCode()).isEqualTo(tag.duplicate().hashCode());
        assertThat(tag.toString()).contains("ClientTag");
    }

    @Test
    void serializesGeneratedProtocolRecordsThroughTheirPublicMessageContract() {
        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(3).setPartition(9);
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(9).setOffsetSum(44L);
        SubscriptionInfoData.TaskOffsetSum offset = new SubscriptionInfoData.TaskOffsetSum().setTopicGroupId(3)
                .setPartition(9).setOffsetSum(44L).setNamedTopology("payments");
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag().setKey(new byte[] {7}).setValue(new byte[] {8, 9});

        assertThat(roundTripTask(task)).isEqualTo(task);
        assertThat(roundTripPartition(partition)).isEqualTo(partition);
        assertThat(roundTripOffset(offset)).isEqualTo(offset);
        assertThat(roundTripTag(tag)).isEqualTo(tag);
    }

    private SubscriptionInfoData.TaskId roundTripTask(SubscriptionInfoData.TaskId value) {
        short version = 6;
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        ObjectSerializationCache cache = new ObjectSerializationCache();
        value.addSize(size, cache, version);
        ByteBufferAccessor writable = new ByteBufferAccessor(java.nio.ByteBuffer.allocate(size.totalSize()));
        value.write(writable, cache, version);
        SubscriptionInfoData.TaskId restored = new SubscriptionInfoData.TaskId(
                new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        restored.read(new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        assertThat(restored.lowestSupportedVersion()).isLessThanOrEqualTo(restored.highestSupportedVersion());
        return restored;
    }

    private SubscriptionInfoData.PartitionToOffsetSum roundTripPartition(SubscriptionInfoData.PartitionToOffsetSum value) {
        short version = 9;
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        ObjectSerializationCache cache = new ObjectSerializationCache();
        value.addSize(size, cache, version);
        ByteBufferAccessor writable = new ByteBufferAccessor(java.nio.ByteBuffer.allocate(size.totalSize()));
        value.write(writable, cache, version);
        SubscriptionInfoData.PartitionToOffsetSum restored = new SubscriptionInfoData.PartitionToOffsetSum(
                new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        restored.read(new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        assertThat(restored.toString()).contains("PartitionToOffsetSum");
        return restored;
    }

    private SubscriptionInfoData.TaskOffsetSum roundTripOffset(SubscriptionInfoData.TaskOffsetSum value) {
        short version = 11;
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        ObjectSerializationCache cache = new ObjectSerializationCache();
        value.addSize(size, cache, version);
        ByteBufferAccessor writable = new ByteBufferAccessor(java.nio.ByteBuffer.allocate(size.totalSize()));
        value.write(writable, cache, version);
        SubscriptionInfoData.TaskOffsetSum restored = new SubscriptionInfoData.TaskOffsetSum(
                new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        restored.read(new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        assertThat(restored.toString()).contains("TaskOffsetSum");
        return restored;
    }

    private SubscriptionInfoData.ClientTag roundTripTag(SubscriptionInfoData.ClientTag value) {
        short version = 11;
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        ObjectSerializationCache cache = new ObjectSerializationCache();
        value.addSize(size, cache, version);
        ByteBufferAccessor writable = new ByteBufferAccessor(java.nio.ByteBuffer.allocate(size.totalSize()));
        value.write(writable, cache, version);
        SubscriptionInfoData.ClientTag restored = new SubscriptionInfoData.ClientTag(
                new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        restored.read(new ByteBufferAccessor(MessageUtil.toByteBuffer(value, version)), version);
        assertThat(restored.lowestSupportedVersion()).isLessThanOrEqualTo(restored.highestSupportedVersion());
        return restored;
    }

    @Test
    void buildsSuppressionAndJoinOptionsForStatefulOperators() {
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.maxRecords(10))
                .withName("bounded")).isNotNull();
        assertThat(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()).withName("final-results")).isNotNull();
        assertThat(Materialized.as(Materialized.StoreType.IN_MEMORY)).isNotNull();
        assertThat(Materialized.as(Stores.persistentWindowStore("joined", Duration.ofHours(1), Duration.ofMinutes(5), false)))
                .isNotNull();
        assertThat(Named.as("first").withName("second")).isNotNull();
        assertThat(TableJoined.with((topic, key, value, partitions) -> 0, (topic, key, value, partitions) -> 0)
                .withName("table-join")).isNotNull();
    }
}
