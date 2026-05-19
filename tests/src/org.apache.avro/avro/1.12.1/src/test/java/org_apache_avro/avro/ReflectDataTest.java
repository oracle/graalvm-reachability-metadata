/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.io.IOException;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectDataTest {
    @Test
    void createsSchemasForReflectiveRecordsAndDefaultValues() {
        ReflectData reflectData = new ReflectData();
        reflectData.setDefaultsGenerated(true);

        Schema schema = reflectData.getSchema(DefaultedRecord.class);

        assertThat(schema.getField("text").defaultVal()).isEqualTo("constructor value");
        assertThat(schema.getField("number").defaultVal()).isEqualTo(7);
    }

    @Test
    void createsSchemasFromTypeAndFieldCustomEncodings() {
        ReflectData reflectData = new ReflectData();

        Schema typeSchema = reflectData.getSchema(TypeEncodedValue.class);
        Schema fieldSchema = reflectData.getSchema(FieldEncodedRecord.class).getField("encoded").schema();

        assertThat(typeSchema.getType()).isEqualTo(Schema.Type.STRING);
        assertThat(fieldSchema.getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void populatesCustomEncodingCacheFromSchemaClassProperty() {
        ReflectData reflectData = new ReflectData();
        Schema schema = Schema.create(Schema.Type.STRING);
        schema.addProp(SpecificData.CLASS_PROP, TypeEncodedValue.class.getName());

        CustomEncoding<?> encoding = reflectData.getCustomEncoding(schema);

        assertThat(encoding).isInstanceOf(TypeEncodedValueEncoding.class);
    }

    @Test
    void createsProtocolFromServiceInterfaceMethods() {
        Protocol protocol = new ReflectData().getProtocol(EchoService.class);

        assertThat(protocol.getMessages()).containsKey("echo");
        assertThat(protocol.getMessages().get("echo").getResponse().getType()).isEqualTo(Schema.Type.STRING);
    }

    @Test
    void createsArrayClassForArraySchemasWithReferenceElements() {
        Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));

        Class<?> arrayClass = new ReflectData().getClass(arraySchema);

        assertThat(arrayClass).isEqualTo(String[].class);
    }

    public interface EchoService {
        String echo(String value);
    }

    public static class DefaultedRecord {
        public String text = "constructor value";
        public int number = 7;

        public DefaultedRecord() {
        }
    }

    @AvroEncode(using = TypeEncodedValueEncoding.class)
    public static class TypeEncodedValue {
        private final String value;

        public TypeEncodedValue() {
            this("default");
        }

        public TypeEncodedValue(String value) {
            this.value = value;
        }
    }

    public static class FieldEncodedRecord {
        @AvroEncode(using = TypeEncodedValueEncoding.class)
        public TypeEncodedValue encoded = new TypeEncodedValue("field");

        public FieldEncodedRecord() {
        }
    }

    public static class TypeEncodedValueEncoding extends CustomEncoding<TypeEncodedValue> {
        public TypeEncodedValueEncoding() {
            this.schema = Schema.create(Schema.Type.STRING);
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            TypeEncodedValue value = (TypeEncodedValue) datum;
            out.writeString(value.value);
        }

        @Override
        protected TypeEncodedValue read(Object reuse, Decoder in) throws IOException {
            return new TypeEncodedValue(in.readString().toString());
        }
    }
}
