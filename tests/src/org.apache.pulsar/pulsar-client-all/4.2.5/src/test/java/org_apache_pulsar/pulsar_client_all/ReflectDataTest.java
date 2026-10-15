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
import org.apache.avro.reflect.AvroEncode;
import org.apache.pulsar.shade.org.apache.avro.reflect.CustomEncoding;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ReflectDataTest {
    @Test
    void derivesDefaultsArraysProtocolsAndCustomEncodings() {
        ReflectData reflectData = new ReflectData().setDefaultsGenerated(true);

        Schema defaultsSchema = reflectData.getSchema(DefaultValues.class);
        Schema arraySchema = reflectData.getSchema(String[].class);
        Protocol protocol = reflectData.getProtocol(GreetingService.class);
        Schema encodedSchema = reflectData.getSchema(EncodedValue.class);
        Schema containerSchema = reflectData.getSchema(EncodedContainer.class);

        assertThat(defaultsSchema.getField("state").defaultVal()).isEqualTo("ready");
        assertThat(reflectData.getClass(arraySchema)).isEqualTo(String[].class);
        assertThat(protocol.getMessages()).containsKey("greet");
        assertThat(encodedSchema.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(reflectData.getCustomEncoding(encodedSchema))
                .isInstanceOf(EncodedValueEncoding.class);
        assertThat(containerSchema.getField("value").schema().getType())
                .isEqualTo(Schema.Type.STRING);
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class DefaultValues {
        public String state = "ready";

        public DefaultValues() { }
    }

    @AvroEncode(using = EncodedValueEncoding.class)
    public static final class EncodedValue {
        private final String value;

        public EncodedValue(String value) {
            this.value = value;
        }
    }

    public static final class EncodedContainer {
        @AvroEncode(using = EncodedValueEncoding.class)
        public EncodedValue value;

        public EncodedContainer() { }
    }

    public static final class EncodedValueEncoding extends CustomEncoding<EncodedValue> {
        public EncodedValueEncoding() {
            schema = Schema.create(Schema.Type.STRING);
            schema.addProp("java-class", EncodedValue.class.getName());
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            out.writeString(((EncodedValue) datum).value);
        }

        @Override
        protected EncodedValue read(Object reuse, Decoder in) throws IOException {
            return new EncodedValue(in.readString());
        }
    }
}
