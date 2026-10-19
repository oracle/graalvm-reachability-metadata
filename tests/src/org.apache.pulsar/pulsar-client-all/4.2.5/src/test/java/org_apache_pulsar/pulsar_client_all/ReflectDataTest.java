/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pulsar.shade.org.apache.avro.Protocol;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.io.Decoder;
import org.apache.pulsar.shade.org.apache.avro.io.Encoder;
import org.apache.pulsar.shade.org.apache.avro.reflect.AvroEncode;
import org.apache.pulsar.shade.org.apache.avro.reflect.CustomEncoding;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectData;
import org.apache.pulsar.shade.org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

public class ReflectDataTest {
    @Test
    void derivesDefaultsFieldsProtocolsAndArrayClasses() {
        ReflectData data = new ReflectData();
        data.setDefaultsGenerated(true);

        Schema recordSchema = data.getSchema(DefaultRecord.class);
        Protocol protocol = data.getProtocol(EchoService.class);
        Class<?> arrayClass = data.getClass(Schema.createArray(Schema.create(Schema.Type.STRING)));

        assertThat(recordSchema.getField("text").defaultVal()).isEqualTo("default text");
        assertThat(recordSchema.getField("count").defaultVal()).isEqualTo(3);
        assertThat(protocol.getMessages()).containsKey("echo");
        assertThat(arrayClass).isEqualTo(String[].class);
    }

    @Test
    void createsAndCachesCustomFieldAndTypeEncodings() {
        ReflectData data = new ReflectData();

        Schema typeSchema = data.getSchema(EncodedValue.class);
        Schema fieldSchema = data.getSchema(EncodedRecord.class).getField("value").schema();
        Schema cacheSchema = Schema.create(Schema.Type.STRING);
        cacheSchema.addProp(SpecificData.CLASS_PROP, EncodedValue.class.getName());
        CustomEncoding<?> cached = data.getCustomEncoding(cacheSchema);

        assertThat(typeSchema.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(fieldSchema.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(cached).isInstanceOf(ValueEncoding.class);
    }

    public interface EchoService {
        String echo(String value);
    }

    public static class DefaultRecord {
        public String text = "default text";
        public int count = 3;

        public DefaultRecord() {
        }
    }

    @AvroEncode(using = ValueEncoding.class)
    public static class EncodedValue {
        private final String text;

        public EncodedValue() {
            this("empty");
        }

        public EncodedValue(String text) {
            this.text = text;
        }
    }

    public static class EncodedRecord {
        @AvroEncode(using = ValueEncoding.class)
        public EncodedValue value = new EncodedValue("field");

        public EncodedRecord() {
        }
    }

    public static class ValueEncoding extends CustomEncoding<EncodedValue> {
        public ValueEncoding() {
            schema = Schema.create(Schema.Type.STRING);
        }

        @Override
        protected void write(Object datum, Encoder output) throws IOException {
            output.writeString(((EncodedValue) datum).text);
        }

        @Override
        protected EncodedValue read(Object reuse, Decoder input) throws IOException {
            return new EncodedValue(input.readString().toString());
        }
    }
}
