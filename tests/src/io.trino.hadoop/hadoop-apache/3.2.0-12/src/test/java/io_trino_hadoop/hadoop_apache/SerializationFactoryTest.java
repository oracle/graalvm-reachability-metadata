/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.SerializationFactory;
import org.apache.hadoop.io.serializer.Serializer;
import org.apache.hadoop.io.serializer.WritableSerialization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SerializationFactoryTest {
    @Test
    void selectsConfiguredWritableSerializationAndRoundTripsText() throws Exception {
        Configuration configuration = new Configuration(false);
        configuration.setStrings("io.serializations", WritableSerialization.class.getName());
        SerializationFactory factory = new SerializationFactory(configuration);
        Serializer<Text> serializer = factory.getSerializer(Text.class);
        Deserializer<Text> deserializer = factory.getDeserializer(Text.class);
        assertNotNull(serializer);
        assertNotNull(deserializer);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        serializer.open(bytes);
        try {
            serializer.serialize(new Text("first payload"));
            serializer.serialize(new Text("second payload"));
        } finally {
            serializer.close();
        }

        deserializer.open(new ByteArrayInputStream(bytes.toByteArray()));
        try {
            Text first = deserializer.deserialize(null);
            assertEquals("first payload", first.toString());
            Text reused = deserializer.deserialize(first);
            assertSame(first, reused);
            assertEquals("second payload", reused.toString());
        } finally {
            deserializer.close();
        }
    }
}
