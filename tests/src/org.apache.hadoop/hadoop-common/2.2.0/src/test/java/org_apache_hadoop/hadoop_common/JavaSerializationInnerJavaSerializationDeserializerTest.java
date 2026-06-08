/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.JavaSerialization;
import org.apache.hadoop.io.serializer.SerializationFactory;
import org.apache.hadoop.io.serializer.Serializer;
import org.junit.jupiter.api.Test;

public class JavaSerializationInnerJavaSerializationDeserializerTest {
    @Test
    void deserializerReadsObjectSerializedByJavaSerialization() throws IOException {
        Configuration conf = new Configuration(false);
        conf.setStrings(
                CommonConfigurationKeys.IO_SERIALIZATIONS_KEY,
                JavaSerialization.class.getName());
        SerializationFactory factory = new SerializationFactory(conf);
        SerializableRecord expected = new SerializableRecord("alpha", 7);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Serializer<SerializableRecord> serializer = factory.getSerializer(SerializableRecord.class);
        assertThat(serializer).isNotNull();
        serializer.open(bytes);
        serializer.serialize(expected);
        serializer.close();

        Deserializer<SerializableRecord> deserializer =
                factory.getDeserializer(SerializableRecord.class);
        assertThat(deserializer).isNotNull();
        deserializer.open(new ByteArrayInputStream(bytes.toByteArray()));
        SerializableRecord actual = deserializer.deserialize(null);
        deserializer.close();

        assertThat(actual).isEqualTo(expected);
    }

    public static final class SerializableRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private int value;

        public SerializableRecord(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializableRecord)) {
                return false;
            }
            SerializableRecord that = (SerializableRecord) other;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}
