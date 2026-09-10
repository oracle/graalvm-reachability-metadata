/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.MessageSizeAccumulator;
import org.apache.kafka.common.protocol.ObjectSerializationCache;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.HostInfo;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.assignment.SubscriptionInfo;
import org.apache.kafka.streams.processor.internals.namedtopology.NamedTopologyStoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises generated protocol records and assignment state through public methods. */
public class ProtocolAndAssignmentApiCoverageTest {
    @Test
    void shouldBuildAndRoundTripGeneratedSubscriptionRecords() {
        SubscriptionInfoData.TaskId taskId = new SubscriptionInfoData.TaskId()
                .setTopicGroupId(2).setPartition(3);
        SubscriptionInfoData.PartitionToOffsetSum partitionOffset = new SubscriptionInfoData.PartitionToOffsetSum()
                .setPartition(3).setOffsetSum(42L);
        SubscriptionInfoData.TaskOffsetSum offsetSum = new SubscriptionInfoData.TaskOffsetSum()
                .setTopicGroupId(2).setPartition(3).setOffsetSum(42L).setNamedTopology("orders")
                .setPartitionToOffsetSum(List.of());
        SubscriptionInfoData.ClientTag tag = new SubscriptionInfoData.ClientTag().setKey(new byte[] {1}).setValue(new byte[] {2});
        SubscriptionInfoData data = new SubscriptionInfoData()
                .setVersion(11).setLatestSupportedVersion(11).setProcessId(Uuid.randomUuid())
                .setPrevTasks(List.of()).setStandbyTasks(List.of())
                .setUserEndPoint(new byte[] {127, 0, 0, 1}).setTaskOffsetSums(List.of(offsetSum))
                .setUniqueField((byte) 7).setErrorCode(0).setClientTags(List.of(tag));

        assertThat(data.apiKey()).isEqualTo((short) -1);
        assertThat(data.prevTasks()).isEmpty();
        assertThat(data.standbyTasks()).isEmpty();
        assertThat(data.taskOffsetSums()).containsExactly(offsetSum);
        assertThat(data.clientTags()).containsExactly(tag);
        assertThat(data.userEndPoint()).containsExactly((byte) 127, (byte) 0, (byte) 0, (byte) 1);
        assertThat(data.uniqueField()).isEqualTo((byte) 7);
        assertThat(data.errorCode()).isZero();
        assertThat(data.duplicate()).isEqualTo(data);
        assertThat(data.hashCode()).isEqualTo(data.duplicate().hashCode());
        assertThat(data.toString()).contains("orders");
        assertThat(data.unknownTaggedFields()).isEmpty();
        assertThat(data.lowestSupportedVersion()).isEqualTo(SubscriptionInfoData.LOWEST_SUPPORTED_VERSION);
        assertThat(data.highestSupportedVersion()).isEqualTo(SubscriptionInfoData.HIGHEST_SUPPORTED_VERSION);

        assertThat(taskId.topicGroupId()).isEqualTo(2);
        assertThat(taskId.partition()).isEqualTo(3);
        assertThat(taskId.duplicate()).isEqualTo(taskId);
        assertThat(taskId.toString()).contains("partition=3");
        assertThat(taskId.unknownTaggedFields()).isEmpty();
        assertThat(offsetSum.topicGroupId()).isEqualTo(2);
        assertThat(offsetSum.partition()).isEqualTo(3);
        assertThat(offsetSum.offsetSum()).isEqualTo(42L);
        assertThat(offsetSum.namedTopology()).isEqualTo("orders");
        assertThat(offsetSum.partitionToOffsetSum()).isEmpty();
        assertThat(offsetSum.duplicate()).isEqualTo(offsetSum);
        assertThat(offsetSum.unknownTaggedFields()).isEmpty();
        assertThat(partitionOffset.partition()).isEqualTo(3);
        assertThat(partitionOffset.offsetSum()).isEqualTo(42L);
        assertThat(partitionOffset.duplicate()).isEqualTo(partitionOffset);
        assertThat(partitionOffset.unknownTaggedFields()).isEmpty();
        assertThat(tag.key()).containsExactly((byte) 1);
        assertThat(tag.value()).containsExactly((byte) 2);
        assertThat(tag.duplicate()).isEqualTo(tag);
        assertThat(tag.unknownTaggedFields()).isEmpty();

        assertThat(new SubscriptionInfoData(read(data, SubscriptionInfoData.HIGHEST_SUPPORTED_VERSION),
                SubscriptionInfoData.HIGHEST_SUPPORTED_VERSION)).isEqualTo(data);
        assertThat(new SubscriptionInfoData.TaskOffsetSum(read(offsetSum,
                SubscriptionInfoData.TaskOffsetSum.HIGHEST_SUPPORTED_VERSION),
                SubscriptionInfoData.TaskOffsetSum.HIGHEST_SUPPORTED_VERSION)).isEqualTo(offsetSum);
        assertThat(new SubscriptionInfoData.PartitionToOffsetSum(read(partitionOffset,
                SubscriptionInfoData.PartitionToOffsetSum.HIGHEST_SUPPORTED_VERSION),
                SubscriptionInfoData.PartitionToOffsetSum.HIGHEST_SUPPORTED_VERSION)).isEqualTo(partitionOffset);
        assertThat(new SubscriptionInfoData.TaskId(read(taskId, SubscriptionInfoData.TaskId.HIGHEST_SUPPORTED_VERSION),
                SubscriptionInfoData.TaskId.HIGHEST_SUPPORTED_VERSION)).isEqualTo(taskId);
        assertThat(new SubscriptionInfoData.ClientTag(read(tag, SubscriptionInfoData.ClientTag.HIGHEST_SUPPORTED_VERSION),
                SubscriptionInfoData.ClientTag.HIGHEST_SUPPORTED_VERSION)).isEqualTo(tag);
    }

    @Test
    void shouldEncodeAssignmentSubscriptionAndNamedStoreQueries() {
        TaskId active = new TaskId(1, 2);
        TaskId standby = new TaskId(2, 3);
        SubscriptionInfo info = new SubscriptionInfo(7, 7, UUID.randomUUID(), "host:1",
                Map.of(active, -2L, standby, 10L), (byte) 1, 0, Map.of("rack", "a"));
        assertThat(info.prevTasks()).contains(active);
        assertThat(info.standbyTasks()).contains(standby);
        assertThat(info.taskOffsetSums()).containsEntry(active, -2L);
        assertThat(info.clientTags()).isEmpty();
        assertThat(info.userEndPoint()).isEqualTo("host:1");
        assertThat(info.encode().remaining()).isPositive();
        assertThat(SubscriptionInfo.decode(info.encode())).isEqualTo(info);
        assertThat(SubscriptionInfo.getActiveTasksFromTaskOffsetSumMap(Map.of(active, -2L))).contains(active);
        assertThat(SubscriptionInfo.getStandbyTasksFromTaskOffsetSumMap(Map.of(standby, 1L))).contains(standby);
        assertThat(info.hashCode()).isEqualTo(SubscriptionInfo.decode(info.encode()).hashCode());
        assertThat(info.toString()).contains("SubscriptionInfoData");

        org.apache.kafka.streams.processor.internals.assignment.AssignmentInfo assignment =
                new org.apache.kafka.streams.processor.internals.assignment.AssignmentInfo(
                        7, List.of(active), Map.of(standby, Set.of(new TopicPartition("orders", 0))),
                        Map.of(new HostInfo("localhost", 8080), Set.of(new TopicPartition("orders", 0))),
                        Map.of(new HostInfo("standby", 8081), Set.of(new TopicPartition("orders", 1))), 42);
        assertThat(org.apache.kafka.streams.processor.internals.assignment.AssignmentInfo.decode(assignment.encode()))
                .isEqualTo(assignment);

        NamedTopologyStoreQueryParameters<org.apache.kafka.streams.state.ReadOnlyKeyValueStore<String, String>> named =
                NamedTopologyStoreQueryParameters.fromNamedTopologyAndStoreNameAndType("orders", "store", QueryableStoreTypes.keyValueStore());
        StoreQueryParameters<?> configured = named.withPartition(1).enableStaleStores();
        assertThat(named.topologyName()).isEqualTo("orders");
        assertThat(configured.partition()).isEqualTo(1);
        assertThat(configured.staleStoresEnabled()).isTrue();
        assertThat(named).isEqualTo(named);
        assertThat(named.hashCode()).isEqualTo(named.hashCode());
    }

    private static ByteBufferAccessor read(org.apache.kafka.common.protocol.Message message, short version) {
        MessageSizeAccumulator size = new MessageSizeAccumulator();
        ObjectSerializationCache cache = new ObjectSerializationCache();
        message.addSize(size, cache, version);
        ByteBuffer buffer = ByteBuffer.allocate(size.totalSize());
        ByteBufferAccessor accessor = new ByteBufferAccessor(buffer);
        message.write(accessor, cache, version);
        accessor.flip();
        return accessor;
    }
}
