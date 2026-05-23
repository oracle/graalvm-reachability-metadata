/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.Serializer;

public class SerializerTest {
    @Test
    void roundTripsSerializedStringUsingByteArray() throws Exception {
        String value = "morphia";

        byte[] serialized = Serializer.serialize(value, false);
        Object deserialized = Serializer.deserialize(serialized, false);

        assertThat(deserialized).isEqualTo(value);
    }

    @Test
    void roundTripsZippedSerializedStringUsingBinary() throws Exception {
        String value = "zipped";

        byte[] serialized = Serializer.serialize(value, true);
        Object deserialized = Serializer.deserialize(new Binary(serialized), true);

        assertThat(deserialized).isEqualTo(value);
    }
}
