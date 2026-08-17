/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.protocol.Message;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.Change;
import org.apache.kafka.streams.kstream.internals.ChangedDeserializer;
import org.apache.kafka.streams.kstream.internals.ChangedSerializer;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.kstream.internals.UnlimitedWindow;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionResponseWrapper;
import org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializationAndValueObjectsCoverageTest {
    @Test
    void changedSerdeRoundTripsBothPublicSerializationForms() {
        ChangedSerializer<String> serializer = new ChangedSerializer<>(Serdes.String().serializer());
        ChangedDeserializer<String> deserializer = new ChangedDeserializer<>(Serdes.String().deserializer());
        Change<String> change = new Change<>("new", "old", true);

        assertThat(deserializer.deserialize("topic", serializer.serialize("topic", change))).isEqualTo(change);
        RecordHeaders headers = new RecordHeaders();
        assertThat(deserializer.deserialize("topic", headers, serializer.serialize("topic", headers, change)))
                .isEqualTo(change);
        serializer.close();
        deserializer.close();
    }

    @Test
    void windowedSerdesRoundTripKeysAndWindows() {
        Windowed<String> session = new Windowed<>("key", new SessionWindow(10, 20));
        SessionWindowedSerializer<String> sessionSerializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> sessionDeserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        assertThat(sessionDeserializer.deserialize("topic", sessionSerializer.serialize("topic", session))).isEqualTo(session);

        Windowed<String> time = new Windowed<>("key", new TimeWindow(10, 20));
        TimeWindowedSerializer<String> timeSerializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> timeDeserializer = new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 10L);
        assertThat(timeDeserializer.deserialize("topic", timeSerializer.serialize("topic", time))).isEqualTo(time);
    }

    @Test
    void subscriptionWrappersHaveStableValueSemantics() {
        long[] hash = {1L, 2L};
        SubscriptionWrapper<String> current = new SubscriptionWrapper<>(hash,
                SubscriptionWrapper.Instruction.PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE, "primary", 3);
        SubscriptionWrapper<String> versioned = new SubscriptionWrapper<>(hash,
                SubscriptionWrapper.Instruction.PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE, "primary", (byte) 1, 3);
        assertThat(current.getHash()).containsExactly(1L, 2L);
        assertThat(current.getInstruction()).isEqualTo(SubscriptionWrapper.Instruction.PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE);
        assertThat(current.getPrimaryKey()).isEqualTo("primary");
        assertThat(current.getPrimaryPartition()).isEqualTo(3);
        assertThat(current.getVersion()).isEqualTo((byte) 1);
        assertThat(current).isEqualTo(current).isEqualTo(versioned);
        assertThat(current.hashCode()).isEqualTo(new SubscriptionWrapper<>(hash,
                SubscriptionWrapper.Instruction.PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE, "primary", 3).hashCode());
        assertThat(current.toString()).contains("primary");
        assertThat(SubscriptionWrapper.Instruction.fromValue(current.getInstruction().getValue()))
                .isEqualTo(current.getInstruction());
        assertThat(SubscriptionWrapper.Instruction.valueOf("PROPAGATE_ONLY_IF_FK_VAL_AVAILABLE"))
                .isEqualTo(current.getInstruction());
        assertThat(SubscriptionWrapper.Instruction.values()).contains(current.getInstruction());

        SubscriptionResponseWrapper<String> response = new SubscriptionResponseWrapper<>(hash, "foreign", 4);
        SubscriptionResponseWrapper<String> responseCopy = new SubscriptionResponseWrapper<>(hash, "foreign", (byte) 0, 4);
        assertThat(response.getOriginalValueHash()).containsExactly(hash);
        assertThat(response.getForeignValue()).isEqualTo("foreign");
        assertThat(response.getPrimaryPartition()).isEqualTo(4);
        assertThat(response.getVersion()).isZero();
        assertThat(response).isEqualTo(responseCopy);
        assertThat(response.hashCode()).isEqualTo(responseCopy.hashCode());
        assertThat(response.toString()).contains("foreign");
    }

    @Test
    void generatedMessagesDuplicateDeeply() {
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag().setKey(new byte[]{1}).setValue(new byte[]{2});
        SubscriptionInfoData.PartitionToOffsetSum partition = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(1).setOffsetSum(12L);
        SubscriptionInfoData.TaskId task = new SubscriptionInfoData.TaskId().setTopicGroupId(2).setPartition(3);
        SubscriptionInfoData.TaskOffsetSum sum = new SubscriptionInfoData.TaskOffsetSum().setTopicGroupId(2)
                .setPartition(3).setOffsetSum(12L).setNamedTopology("named").setPartitionToOffsetSum(java.util.List.of(partition));
        SubscriptionInfoData data = new SubscriptionInfoData().setVersion(11).setPrevTasks(java.util.List.of(task))
                .setTaskOffsetSums(java.util.List.of(sum)).setClientTags(java.util.List.of(tag));

        assertDuplicate(data, data.duplicate());
        assertDuplicate(tag, tag.duplicate());
        assertDuplicate(partition, partition.duplicate());
        assertDuplicate(task, task.duplicate());
        assertDuplicate(sum, sum.duplicate());
    }

    @Test
    void windowsAndTimestampExtractorEnforceTheirContracts() {
        assertThat(new SessionWindow(10, 20).overlap(new SessionWindow(20, 30))).isTrue();
        assertThat(new TimeWindow(10, 20).overlap(new TimeWindow(19, 30))).isTrue();
        assertThat(new UnlimitedWindow(10).overlap(new UnlimitedWindow(100))).isTrue();
        FailOnInvalidTimestamp extractor = new FailOnInvalidTimestamp();
        ConsumerRecord<Object, Object> valid = new ConsumerRecord<>("topic", 0, 1L, 1L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, "key", "value",
                new RecordHeaders(), java.util.Optional.empty());
        assertThat(extractor.extract(valid, 0)).isEqualTo(1L);
        ConsumerRecord<Object, Object> invalid = new ConsumerRecord<>("topic", 0, 0L, -1L,
                org.apache.kafka.common.record.TimestampType.CREATE_TIME, -1, -1, "key", "value", new RecordHeaders(), java.util.Optional.empty());
        assertThatThrownBy(() -> extractor.extract(invalid, 0)).isInstanceOf(StreamsException.class);
    }

    private static void assertDuplicate(Message original, Message duplicate) {
        assertThat(duplicate).isEqualTo(original).isNotSameAs(original);
    }
}
