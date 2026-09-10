/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.internals.PrefixedWindowKeySchemas;
import org.apache.kafka.streams.state.internals.TimestampedKeyAndJoinSide;
import org.apache.kafka.streams.state.internals.TimestampedKeyAndJoinSideSerde;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives public state-key and timestamped-join serde entries through their decoding paths. */
public class DeepStateStorageCoverageTest {
    @Test
    void shouldDecodeBothPrefixedWindowKeyLayouts() {
        Bytes key = Bytes.wrap(new byte[] {1, 2, 3});
        Windowed<Bytes> window = new Windowed<>(key, new TimeWindow(1_000L, 1_100L));

        byte[] keyFirst = PrefixedWindowKeySchemas.KeyFirstWindowKeySchema
                .toStoreKeyBinary(window, 7)
                .get();
        Windowed<Bytes> decodedKeyFirst = PrefixedWindowKeySchemas.KeyFirstWindowKeySchema
                .fromStoreBytesKey(keyFirst, 100L);
        assertThat(decodedKeyFirst.key()).isEqualTo(key);
        assertThat(decodedKeyFirst.window().start()).isEqualTo(1_000L);
        assertThat(decodedKeyFirst.window().end()).isEqualTo(1_100L);

        byte[] timeFirst = PrefixedWindowKeySchemas.TimeFirstWindowKeySchema
                .toStoreKeyBinary(window, 9)
                .get();
        Windowed<Bytes> decodedTimeFirst = PrefixedWindowKeySchemas.TimeFirstWindowKeySchema
                .fromStoreBytesKey(timeFirst, 100L);
        assertThat(decodedTimeFirst.key()).isEqualTo(key);
        assertThat(decodedTimeFirst.window().start()).isEqualTo(1_000L);
        assertThat(decodedTimeFirst.window().end()).isEqualTo(1_100L);
        assertThat(Arrays.equals(keyFirst, timeFirst)).isFalse();
    }

    @Test
    void shouldDeserializeTimestampedKeyAndJoinSide() {
        TimestampedKeyAndJoinSideSerde<String> serde = new TimestampedKeyAndJoinSideSerde<>(
                org.apache.kafka.common.serialization.Serdes.String());
        TimestampedKeyAndJoinSide<String> original = TimestampedKeyAndJoinSide.make(true, "join-key", 42L);

        byte[] encoded = serde.serializer().serialize("topic", original);
        TimestampedKeyAndJoinSide<String> decoded = serde.deserializer().deserialize("topic", encoded);

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.isLeftSide()).isTrue();
        assertThat(decoded.getKey()).isEqualTo("join-key");
        assertThat(decoded.getTimestamp()).isEqualTo(42L);
        serde.serializer().close();
        serde.deserializer().close();
    }
}
