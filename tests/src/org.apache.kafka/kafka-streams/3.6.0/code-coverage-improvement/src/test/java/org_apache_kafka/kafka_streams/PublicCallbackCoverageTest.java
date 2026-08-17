/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.internals.NamedInternal;
import org.apache.kafka.streams.processor.BatchingStateRestoreCallback;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCallbackCoverageTest {
    @Test
    void batchingRestoreRejectsSingleRecordUseAndAcceptsBatches() {
        List<KeyValue<byte[], byte[]>> restored = new ArrayList<>();
        BatchingStateRestoreCallback callback = restored::addAll;
        byte[] key = {1};
        byte[] value = {2};

        assertThatThrownBy(() -> callback.restore(key, value))
                .isInstanceOf(UnsupportedOperationException.class);
        callback.restoreAll(List.of(KeyValue.pair(key, value)));
        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).key).isSameAs(key);
        assertThat(restored.get(0).value).isSameAs(value);
    }

    @Test
    void streamPartitionerAdaptsLegacyPartitionToOptionalSet() {
        StreamPartitioner<String, String> partitioner = (topic, key, value, partitions) -> 2;
        Optional<Set<Integer>> selected = partitioner.partitions("output", "key", "value", 4);
        assertThat(selected).contains(Set.of(2));

        StreamPartitioner<String, String> broadcast = (topic, key, value, partitions) -> null;
        assertThat(broadcast.partitions("output", "key", "value", 4)).isEmpty();
    }

    @Test
    void namedOperationsReturnNewConfigurationsWithRequestedNames() {
        NamedInternal initial = NamedInternal.with("initial");
        assertThat(initial.name()).isEqualTo("initial");
        assertThat(initial.withName("renamed").name()).isEqualTo("renamed");
        assertThat(Named.as("public").withName("updated")).isNotNull();

        Branched<String, String> function = Branched.<String, String>withFunction(stream -> stream)
                .withName("function");
        Branched<String, String> consumer = Branched.<String, String>withConsumer(stream -> { }).withName("consumer");
        assertThat(function).isNotSameAs(consumer);
    }
}
