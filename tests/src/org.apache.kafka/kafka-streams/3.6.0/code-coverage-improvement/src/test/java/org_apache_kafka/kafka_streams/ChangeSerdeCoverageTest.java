/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.util.Arrays;
import java.util.Collections;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.kstream.internals.Change;
import org.apache.kafka.streams.kstream.internals.ChangedDeserializer;
import org.apache.kafka.streams.kstream.internals.ChangedSerializer;
import org.apache.kafka.streams.kstream.internals.FullChangeSerde;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChangeSerdeCoverageTest {
    @Test
    public void changedSerdeRoundTripsEveryChangeShape() {
        StringSerializer innerSerializer = new StringSerializer();
        StringDeserializer innerDeserializer = new StringDeserializer();
        ChangedSerializer<String> serializer = new ChangedSerializer<>(innerSerializer);
        ChangedDeserializer<String> deserializer = new ChangedDeserializer<>(innerDeserializer);
        serializer.configure(Collections.emptyMap(), false);

        assertSame(innerSerializer, serializer.inner());
        assertSame(innerDeserializer, deserializer.inner());
        for (Change<String> change : Arrays.asList(
                new Change<>("new", null),
                new Change<>(null, "old"),
                new Change<>("new", "old", true))) {
            byte[] plain = serializer.serialize("changes", change);
            byte[] withHeaders = serializer.serialize("changes", new RecordHeaders(), change);
            assertEquals(change, deserializer.deserialize("changes", plain));
            assertEquals(change, deserializer.deserialize("changes", new RecordHeaders(), withHeaders));
            assertEquals(change.hashCode(), deserializer.deserialize("changes", plain).hashCode());
            assertTrue(change.toString().contains("<-"));
        }
        assertFalse(new Change<>("a", "b").equals(new Change<>("b", "a")));
        serializer.close();
        deserializer.close();
    }

    @Test
    public void fullChangeSerdeSupportsCurrentAndLegacyFormats() {
        FullChangeSerde<String> serde = FullChangeSerde.wrap(Serdes.String());
        Change<String> original = new Change<>("new", "old", true);
        Change<byte[]> encoded = serde.serializeParts("topic", original);
        Change<String> decoded = serde.deserializeParts("topic", encoded);

        assertSame(Serdes.String().getClass(), serde.innerSerde().getClass());
        assertEquals(original, decoded);
        byte[] legacy = new byte[] {0, 0, 0, 3, 'o', 'l', 'd', 0, 0, 0, 3, 'n', 'e', 'w'};
        Change<byte[]> decomposed = FullChangeSerde.decomposeLegacyFormattedArrayIntoChangeArrays(legacy);
        assertArrayEquals("new".getBytes(), decomposed.newValue);
        assertArrayEquals("old".getBytes(), decomposed.oldValue);
    }
}
