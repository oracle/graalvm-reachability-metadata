/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectDatumReaderTest {
    @Test
    void createsJavaArrayForSchemaWithReflectElementClass() {
        Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));
        arraySchema.addProp(SpecificData.ELEMENT_PROP, String.class.getName());
        ExposedReflectDatumReader reader = new ExposedReflectDatumReader(arraySchema);

        Object array = reader.createArray(3, arraySchema);

        assertThat(array).isInstanceOf(String[].class);
        assertThat((String[]) array).hasSize(3);
    }

    private static final class ExposedReflectDatumReader extends ReflectDatumReader<Object> {
        private ExposedReflectDatumReader(Schema schema) {
            super(schema);
        }

        private Object createArray(int size, Schema schema) {
            return newArray(null, size, schema);
        }
    }
}
