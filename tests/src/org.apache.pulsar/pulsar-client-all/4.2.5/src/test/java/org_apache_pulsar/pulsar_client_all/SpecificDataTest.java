/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.avro.Protocol;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.generic.GenericData.InstanceSupplier;
import org.apache.pulsar.shade.org.apache.avro.specific.SpecificData;
import org.apache.pulsar.shade.org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

public class SpecificDataTest {
    @Test
    void readsGeneratedModelSchemaAndProtocolFields() {
        SpecificData model = SpecificData.getForClass(GeneratedRecord.class);
        Schema schema = SpecificData.get().getSchema(GeneratedRecord.class);
        Protocol protocol = SpecificData.get().getProtocol(GeneratedService.class);

        assertThat(model).isSameAs(GeneratedRecord.MODEL$);
        assertThat(schema).isSameAs(GeneratedRecord.SCHEMA$);
        assertThat(protocol.getMessages()).containsKey("lookup");
    }

    @Test
    void constructsSpecificRecordsAndUsesCachedInstanceSupplier() {
        SchemaConstructedRecord constructed = (SchemaConstructedRecord) SpecificData.newInstance(
                SchemaConstructedRecord.class, SchemaConstructedRecord.SCHEMA$);
        InstanceSupplier supplier = SpecificData.get().getNewRecordSupplier(GeneratedRecord.SCHEMA$);
        Object supplied = supplier.newInstance(null, GeneratedRecord.SCHEMA$);

        assertThat(constructed.constructorSchema).isSameAs(SchemaConstructedRecord.SCHEMA$);
        assertThat(supplied).isInstanceOf(GeneratedRecord.class);
        assertThat(supplier.newInstance(supplied, GeneratedRecord.SCHEMA$)).isSameAs(supplied);
    }

    public interface GeneratedService {
        Protocol PROTOCOL = Protocol.parse("""
                {
                  "protocol": "GeneratedService",
                  "namespace": "org_apache_pulsar.pulsar_client_all",
                  "messages": {
                    "lookup": {
                      "request": [{"name": "key", "type": "string"}],
                      "response": "string"
                    }
                  }
                }
                """);
    }

    public static class GeneratedRecord extends SpecificRecordBase {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "GeneratedRecord",
                  "namespace": "org_apache_pulsar.pulsar_client_all.SpecificDataTest",
                  "fields": []
                }
                """);
        public static final SpecificData MODEL$ = new SpecificData();

        public GeneratedRecord() {
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            throw new IndexOutOfBoundsException("field " + field);
        }

        @Override
        public void put(int field, Object value) {
            throw new IndexOutOfBoundsException("field " + field);
        }
    }

    public static class SchemaConstructedRecord extends SpecificRecordBase
            implements SpecificData.SchemaConstructable {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "SchemaConstructedRecord",
                  "namespace": "org_apache_pulsar.pulsar_client_all.SpecificDataTest",
                  "fields": []
                }
                """);
        private final Schema constructorSchema;

        public SchemaConstructedRecord(Schema schema) {
            constructorSchema = schema;
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            throw new IndexOutOfBoundsException("field " + field);
        }

        @Override
        public void put(int field, Object value) {
            throw new IndexOutOfBoundsException("field " + field);
        }
    }
}
