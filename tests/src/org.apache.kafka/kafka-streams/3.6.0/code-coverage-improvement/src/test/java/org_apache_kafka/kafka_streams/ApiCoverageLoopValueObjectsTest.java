/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.CombinedKey;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.CombinedKeySchema;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionResponseWrapper;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper;
import org.apache.kafka.streams.internals.UpgradeFromValues;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.internals.ValueAndTimestampSerde;
import org.apache.kafka.streams.state.internals.ValueAndTimestampSerializer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises foreign-key records, protocol values, and timestamped serialization contracts. */
public class ApiCoverageLoopValueObjectsTest {
    @Test
    void shouldRoundTripForeignKeyAndSubscriptionValues() {
        CombinedKeySchema<String, String> schema = new CombinedKeySchema<>(
                () -> "foreign-topic", Serdes.String(), () -> "primary-topic", Serdes.String());
        schema.init(null);
        byte[] foreign = Serdes.String().serializer().serialize("foreign-topic", "foreign");
        byte[] primary = Serdes.String().serializer().serialize("primary-topic", "primary");
        byte[] encoded = ByteBuffer.allocate(4 + foreign.length + primary.length)
                .putInt(foreign.length).put(foreign).put(primary).array();
        CombinedKey<String, String> key = schema.fromBytes(Bytes.wrap(encoded));
        assertThat(key.getForeignKey()).isEqualTo("foreign");
        assertThat(key.getPrimaryKey()).isEqualTo("primary");
        assertThat(key).isEqualTo(schema.fromBytes(Bytes.wrap(encoded)));
        assertThat(key.hashCode()).isEqualTo(schema.fromBytes(Bytes.wrap(encoded)).hashCode());
        assertThat(key.toString()).contains("foreign", "primary");

        SubscriptionWrapper<String> subscription = new SubscriptionWrapper<>(
                new long[] {1L, 2L}, SubscriptionWrapper.Instruction.DELETE_KEY_AND_PROPAGATE, "primary", (byte) 0, Integer.valueOf(4));
        SubscriptionWrapper<String> oldForm = new SubscriptionWrapper<>(
                new long[] {1L, 2L}, SubscriptionWrapper.Instruction.DELETE_KEY_AND_PROPAGATE, "primary", 5);
        assertThat(subscription.getHash()).containsExactly(1L, 2L);
        assertThat(subscription.getInstruction()).isEqualTo(SubscriptionWrapper.Instruction.DELETE_KEY_AND_PROPAGATE);
        assertThat(subscription.getPrimaryKey()).isEqualTo("primary");
        assertThat(subscription.getVersion()).isEqualTo((byte) 0);
        assertThat(subscription.getPrimaryPartition()).isEqualTo(4);
        assertThat(subscription).isNotEqualTo(oldForm);
        assertThat(subscription.hashCode()).isNotEqualTo(0);
        assertThat(subscription.toString()).contains("primary");
        for (SubscriptionWrapper.Instruction instruction : SubscriptionWrapper.Instruction.values()) {
            assertThat(SubscriptionWrapper.Instruction.valueOf(instruction.name())).isSameAs(instruction);
            assertThat(SubscriptionWrapper.Instruction.fromValue(instruction.getValue())).isSameAs(instruction);
        }

        SubscriptionResponseWrapper<String> response = new SubscriptionResponseWrapper<>(
                new long[] {5L}, "foreign-value", (byte) 0, 4);
        SubscriptionResponseWrapper<String> responseOld = new SubscriptionResponseWrapper<>(
                new long[] {5L}, "foreign-value", 4);
        assertThat(response.getOriginalValueHash()).containsExactly(5L);
        assertThat(response.getForeignValue()).isEqualTo("foreign-value");
        assertThat(response.getVersion()).isEqualTo((byte) 0);
        assertThat(response.getPrimaryPartition()).isEqualTo(4);
        assertThat(response).isEqualTo(responseOld);
        assertThat(response.hashCode()).isEqualTo(responseOld.hashCode());
        assertThat(response.toString()).contains("foreign-value");
    }

    @Test
    void shouldPreserveGeneratedProtocolMessageVersionsAndCopies() {
        SubscriptionInfoData data = new SubscriptionInfoData().setVersion(2).setLatestSupportedVersion(4);
        SubscriptionInfoData copy = data.duplicate();
        assertThat(copy).isEqualTo(data);
        assertThat(copy).isNotSameAs(data);
        assertThat(data.lowestSupportedVersion()).isLessThanOrEqualTo(data.highestSupportedVersion());

        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(7).setPartition(2);
        SubscriptionInfoData.TaskId taskCopy = task.duplicate();
        assertThat(taskCopy).isEqualTo(task);
        assertThat(taskCopy).isNotSameAs(task);
        assertThat(task.hashCode()).isEqualTo(taskCopy.hashCode());
        assertThat(task.lowestSupportedVersion()).isLessThanOrEqualTo(task.highestSupportedVersion());

        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag();
        assertThat(tag.duplicate()).isEqualTo(tag);
        assertThat(tag.lowestSupportedVersion()).isLessThanOrEqualTo(tag.highestSupportedVersion());
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum();
        assertThat(partition.duplicate()).isEqualTo(partition);
        assertThat(partition.lowestSupportedVersion()).isLessThanOrEqualTo(partition.highestSupportedVersion());
        SubscriptionInfoData.TaskOffsetSum offset = new SubscriptionInfoData.TaskOffsetSum();
        assertThat(offset.duplicate()).isEqualTo(offset);
        assertThat(offset.lowestSupportedVersion()).isLessThanOrEqualTo(offset.highestSupportedVersion());
    }

    @Test
    void shouldHandleTimestampAndRecordTimeContracts() {
        ValueAndTimestampSerde<String> serde = new ValueAndTimestampSerde<>(Serdes.String());
        byte[] first = serde.serializer().serialize("topic", ValueAndTimestamp.make("value", 10));
        byte[] sameValueLater = serde.serializer().serialize("topic", ValueAndTimestamp.make("value", 11));
        byte[] changed = serde.serializer().serialize("topic", ValueAndTimestamp.make("other", 12));
        assertThat(ValueAndTimestampSerializer.valuesAreSameAndTimeIsIncreasing(first, sameValueLater)).isTrue();
        assertThat(ValueAndTimestampSerializer.valuesAreSameAndTimeIsIncreasing(first, changed)).isFalse();
        serde.configure(Map.of(), false);
        serde.close();

        ProcessorContext context = NativeCoverageFixtures.processorContext(123L);
        assertThat(context.timestamp()).isEqualTo(123L);
        Windowed<String> windowed = new Windowed<>("key", new TimeWindow(100, 200));
        assertThat(windowed.window().end()).isEqualTo(200L);

        assertThat(UpgradeFromValues.getValueFromString("3.4")).isEqualTo(UpgradeFromValues.UPGRADE_FROM_34);
        assertThat(UpgradeFromValues.valueOf("UPGRADE_FROM_34")).isEqualTo(UpgradeFromValues.UPGRADE_FROM_34);
        assertThat(UpgradeFromValues.values()).contains(UpgradeFromValues.UPGRADE_FROM_34);
        assertThat(new Windowed<>("key", new SessionWindow(1, 2)).toString()).contains("key");
    }
}
