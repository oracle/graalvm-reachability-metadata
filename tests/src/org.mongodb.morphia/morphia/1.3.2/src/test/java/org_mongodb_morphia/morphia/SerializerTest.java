/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.Serializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerTest {
    @Test
    public void roundTripsUncompressedSerializableValue() throws Exception {
        final ArrayList<String> value = new ArrayList<>();
        value.add("morphia");
        value.add("serializer");

        final byte[] serialized = Serializer.serialize(value, false);
        final Object restored = Serializer.deserialize(serialized, false);

        assertThat(serialized).isNotEmpty();
        assertThat(restored).isEqualTo(value);
    }

    @Test
    public void roundTripsCompressedBinaryValue() throws Exception {
        final ArrayList<Integer> value = new ArrayList<>(List.of(1, 3, 2));

        final byte[] serialized = Serializer.serialize(value, true);
        final Object restored = Serializer.deserialize(new Binary(serialized), true);

        assertThat(serialized).isNotEmpty();
        assertThat(restored).isEqualTo(value);
    }
}
