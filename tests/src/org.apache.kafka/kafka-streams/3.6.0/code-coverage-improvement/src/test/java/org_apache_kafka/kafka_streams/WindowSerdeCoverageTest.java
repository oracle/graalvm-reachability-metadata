/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.StateSerdes;
import org.apache.kafka.streams.state.internals.PrefixedWindowKeySchemas;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WindowSerdeCoverageTest {
    @Test
    void sessionSerializerAndDeserializerRoundTripAndExposeBaseKey() {
        SessionWindowedSerializer<String> serializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> deserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        serializer.configure(Map.of(), true);
        deserializer.configure(Map.of(), true);
        Windowed<String> original = new Windowed<>("account", new SessionWindow(10, 20));

        byte[] serialized = serializer.serialize("topic", original);
        assertThat(serializer.serializeBaseKey("topic", original)).isEqualTo(Serdes.String().serializer().serialize("topic", "account"));
        assertThat(deserializer.deserialize("topic", serialized)).isEqualTo(original);
        assertThat((Object) deserializer.deserialize("topic", serialized)).isEqualTo(original);
        serializer.close();
        deserializer.close();
    }

    @Test
    void prefixedWindowSchemasDecodeKeyFirstAndTimeFirstStoreKeys() {
        StateSerdes<String, String> serdes = new StateSerdes<>("windows", Serdes.String(), Serdes.String());
        Windowed<String> window = new Windowed<>("account", new TimeWindow(200L, 300L));

        Bytes keyFirst = PrefixedWindowKeySchemas.KeyFirstWindowKeySchema.toStoreKeyBinary(window, 7, serdes);
        Windowed<Bytes> decodedKeyFirst = PrefixedWindowKeySchemas.KeyFirstWindowKeySchema
                .fromStoreBytesKey(keyFirst.get(), 100L);
        assertThat(decodedKeyFirst.key().get()).isEqualTo(Serdes.String().serializer()
                .serialize("windows", "account"));
        assertThat(decodedKeyFirst.window().start()).isEqualTo(200L);

        Bytes timeFirst = PrefixedWindowKeySchemas.TimeFirstWindowKeySchema.toStoreKeyBinary(window, 7, serdes);
        Windowed<Bytes> decodedTimeFirst = PrefixedWindowKeySchemas.TimeFirstWindowKeySchema
                .fromStoreBytesKey(timeFirst.get(), 100L);
        assertThat(decodedTimeFirst.key().get()).isEqualTo(Serdes.String().serializer()
                .serialize("windows", "account"));
        assertThat(decodedTimeFirst.window().start()).isEqualTo(200L);
    }

    @Test
    void timeSerializerAndDeserializerRoundTripWithConfiguredWindowSize() {
        TimeWindowedSerializer<String> serializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> deserializer = new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 100L);
        serializer.configure(Map.of(), true);
        deserializer.configure(Map.of(), true);
        Windowed<String> original = new Windowed<>("account", new TimeWindow(200, 300));

        byte[] serialized = serializer.serialize("topic", original);
        assertThat(serializer.serializeBaseKey("topic", original)).isEqualTo(Serdes.String().serializer().serialize("topic", "account"));
        assertThat(deserializer.getWindowSize()).isEqualTo(100L);
        assertThat(deserializer.deserialize("topic", serialized)).isEqualTo(original);
        assertThat((Object) deserializer.deserialize("topic", serialized)).isEqualTo(original);
        serializer.close();
        deserializer.close();

        WindowedSerdes windowedSerdes = new WindowedSerdes();
        assertThat(windowedSerdes).isNotNull();
        assertThat(WindowedSerdes.sessionWindowedSerdeFrom(String.class)).isNotNull();
        assertThat(new WindowedSerdes.SessionWindowedSerde<>(Serdes.String())).isNotNull();
    }
}
