/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.Message;
import org.apache.kafka.common.protocol.MessageSizeAccumulator;
import org.apache.kafka.common.protocol.ObjectSerializationCache;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedSubscriptionProtocolCoverageTest {
    @Test
    void subscriptionAndNestedRecordsRoundTripAtTheirLatestVersions() {
        SubscriptionInfoData.TaskId taskId = new SubscriptionInfoData.TaskId()
                .setTopicGroupId(4).setPartition(7);
        SubscriptionInfoData.PartitionToOffsetSum partitionOffset =
                new SubscriptionInfoData.PartitionToOffsetSum().setPartition(7).setOffsetSum(91L);
        SubscriptionInfoData.TaskOffsetSum taskOffset = new SubscriptionInfoData.TaskOffsetSum()
                .setTopicGroupId(4).setPartition(7).setOffsetSum(91L).setNamedTopology("orders");
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag()
                .setKey(new byte[] {1, 2}).setValue(new byte[] {3, 4});
        SubscriptionInfoData subscription = new SubscriptionInfoData()
                .setPrevTasks(List.of(taskId)).setStandbyTasks(List.of(taskId.duplicate()));

        assertRoundTrip(taskId, (short) 6);
        assertRoundTrip(partitionOffset, (short) 9);
        assertRoundTrip(taskOffset, (short) 11);
        assertRoundTrip(tag, (short) 11);
        SubscriptionInfoData decoded = roundTrip(subscription, (short) 6);
        assertThat(decoded).isEqualTo(subscription).hasSameHashCodeAs(subscription);
        assertThat(decoded.prevTasks()).containsExactly(taskId);
        assertThat(decoded.standbyTasks()).containsExactly(taskId);
        assertThat(decoded.uniqueField()).isZero();
        assertThat(decoded.duplicate()).isEqualTo(decoded).isNotSameAs(decoded);
        assertThat(((Message) decoded).duplicate()).isEqualTo(decoded);
        assertThat(taskOffset.topicGroupId()).isEqualTo(4);
        assertThat(taskOffset.partition()).isEqualTo(7);
        assertThat(taskOffset.offsetSum()).isEqualTo(91L);
        assertThat(taskOffset.namedTopology()).isEqualTo("orders");
        assertThat(taskOffset.partitionToOffsetSum()).isEmpty();
        assertThat(taskOffset.setPartitionToOffsetSum(List.of(partitionOffset)).partitionToOffsetSum())
                .containsExactly(partitionOffset);
        assertThat(partitionOffset.partition()).isEqualTo(7);
        assertThat(partitionOffset.offsetSum()).isEqualTo(91L);
        assertThat(taskId.topicGroupId()).isEqualTo(4);
        assertThat(taskId.partition()).isEqualTo(7);
        assertThat(tag.key()).containsExactly(1, 2);
        assertThat(tag.value()).containsExactly(3, 4);
        assertThat(taskOffset.toString()).contains("orders");
        assertThat(decoded.toString()).contains("prevTasks");
        assertThat(decoded.unknownTaggedFields()).isEmpty();
        assertThat(decoded.apiKey()).isEqualTo((short) -1);
        assertThat(decoded.lowestSupportedVersion()).isLessThanOrEqualTo(decoded.highestSupportedVersion());
    }

    private static <T extends Message> void assertRoundTrip(T message, short version) {
        T decoded = roundTrip(message, version);
        assertThat(decoded).isEqualTo(message).hasSameHashCodeAs(message);
        assertThat(decoded.toString()).isNotBlank();
        assertThat(decoded.unknownTaggedFields()).isEmpty();
        assertThat(decoded.lowestSupportedVersion()).isLessThanOrEqualTo(version);
        assertThat(decoded.highestSupportedVersion()).isGreaterThanOrEqualTo(version);
        assertThat(message.duplicate()).isEqualTo(message).isNotSameAs(message);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Message> T roundTrip(T message, short version) {
        ObjectSerializationCache cache = new ObjectSerializationCache();
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        message.addSize(size, cache, version);
        ByteBuffer buffer = ByteBuffer.allocate(size.totalSize());
        message.write(new ByteBufferAccessor(buffer), cache, version);
        buffer.flip();
        ByteBufferAccessor accessor = new ByteBufferAccessor(buffer);
        if (message instanceof SubscriptionInfoData.TaskId) {
            return (T) new SubscriptionInfoData.TaskId(accessor, version);
        }
        if (message instanceof SubscriptionInfoData.PartitionToOffsetSum) {
            return (T) new SubscriptionInfoData.PartitionToOffsetSum(accessor, version);
        }
        if (message instanceof SubscriptionInfoData.TaskOffsetSum) {
            return (T) new SubscriptionInfoData.TaskOffsetSum(accessor, version);
        }
        if (message instanceof SubscriptionInfoData.ClientTag) {
            return (T) new SubscriptionInfoData.ClientTag(accessor, version);
        }
        if (message instanceof SubscriptionInfoData) {
            return (T) new SubscriptionInfoData(accessor, version);
        }
        throw new AssertionError("Unsupported message type: " + message.getClass().getName());
    }
}
