/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.InstanceSupplier;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecificDataTest {
    @Test
    void readsGeneratedSchemaModelAndProtocolFields() {
        SpecificData model = SpecificData.getForClass(GeneratedRecordNoArg.class);
        Schema schema = SpecificData.get().getSchema(GeneratedRecordNoArg.class);
        Protocol protocol = SpecificData.get().getProtocol(GeneratedService.class);

        assertThat(model).isSameAs(GeneratedRecordNoArg.MODEL$);
        assertThat(schema.getType()).isEqualTo(Schema.Type.RECORD);
        assertThat(schema.getName()).isEqualTo("GeneratedRecordNoArg");
        assertThat(protocol.getMessages()).containsKey("echo");
    }

    @Test
    void createsSpecificRecordInstancesWithConstructorsAndCachedSuppliers() {
        GeneratedSchemaConstructable constructed = (GeneratedSchemaConstructable) SpecificData
                .newInstance(GeneratedSchemaConstructable.class, GeneratedSchemaConstructable.SCHEMA$);

        InstanceSupplier supplier = SpecificData.get().getNewRecordSupplier(GeneratedRecordNoArg.SCHEMA$);
        Object supplied = supplier.newInstance(null, GeneratedRecordNoArg.SCHEMA$);
        Object reused = supplier.newInstance(supplied, GeneratedRecordNoArg.SCHEMA$);

        assertThat(constructed.constructorSchema).isSameAs(GeneratedSchemaConstructable.SCHEMA$);
        assertThat(supplied).isInstanceOf(GeneratedRecordNoArg.class);
        assertThat(reused).isSameAs(supplied);
    }

    public interface GeneratedService {
        Protocol PROTOCOL = Protocol.parse("""
                {
                  "protocol": "GeneratedService",
                  "namespace": "org_apache_avro.avro",
                  "messages": {
                    "echo": {
                      "request": [
                        {"name": "value", "type": "string"}
                      ],
                      "response": "string"
                    }
                  }
                }
                """);
    }

    public static class GeneratedRecordNoArg extends SpecificRecordBase {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "GeneratedRecordNoArg",
                  "namespace": "org_apache_avro.avro.SpecificDataTest",
                  "fields": []
                }
                """);
        public static final SpecificData MODEL$ = new SpecificData();

        public GeneratedRecordNoArg() {
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }

        @Override
        public void put(int field, Object value) {
            throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    public static class GeneratedSchemaConstructable extends SpecificRecordBase
            implements SpecificData.SchemaConstructable {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "GeneratedSchemaConstructable",
                  "namespace": "org_apache_avro.avro.SpecificDataTest",
                  "fields": []
                }
                """);

        private final Schema constructorSchema;

        public GeneratedSchemaConstructable(Schema schema) {
            this.constructorSchema = schema;
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }

        @Override
        public void put(int field, Object value) {
            throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }
}
