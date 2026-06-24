/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.hadoop.io.serializer.Deserializer;
import org.apache.hadoop.io.serializer.avro.AvroSpecificSerialization;
import org.junit.jupiter.api.Test;

public class AvroSpecificSerializationTest implements SpecificRecord {
    private static final Schema SCHEMA = new Schema.Parser().parse(
            "{"
                    + "\"type\":\"record\","
                    + "\"name\":\"AvroSpecificSerializationTest\","
                    + "\"namespace\":\"org_apache_hadoop.hadoop_common\","
                    + "\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]"
                    + "}");

    private CharSequence value = "";

    @Test
    void createsDeserializerForSpecificRecordClass() {
        AvroSpecificSerialization serialization = new AvroSpecificSerialization();

        Deserializer<SpecificRecord> deserializer =
                serialization.getDeserializer(specificRecordClass());

        assertThat(serialization.accept(AvroSpecificSerializationTest.class)).isTrue();
        assertThat(deserializer).isNotNull();
    }

    @Override
    public void put(int field, Object fieldValue) {
        if (field != 0) {
            throw new IndexOutOfBoundsException("Unsupported field index: " + field);
        }
        value = (CharSequence) fieldValue;
    }

    @Override
    public Object get(int field) {
        if (field != 0) {
            throw new IndexOutOfBoundsException("Unsupported field index: " + field);
        }
        return value;
    }

    @Override
    public Schema getSchema() {
        return SCHEMA;
    }

    @SuppressWarnings("unchecked")
    private static Class<SpecificRecord> specificRecordClass() {
        return (Class<SpecificRecord>) (Class<?>) AvroSpecificSerializationTest.class;
    }
}
