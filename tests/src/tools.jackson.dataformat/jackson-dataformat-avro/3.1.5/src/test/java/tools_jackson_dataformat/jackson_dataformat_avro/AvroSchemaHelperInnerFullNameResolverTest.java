/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_dataformat.jackson_dataformat_avro;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.schema.AvroSchemaHelper;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroSchemaHelperInnerFullNameResolverTest {
    @Test
    void resolvesAndRoundTripsNestedRecordSchema() throws Exception {
        AvroMapper mapper = new AvroMapper();
        AvroSchema schema = mapper.schemaFor(NestedRecord.class);

        String fullName = AvroSchemaHelper.getFullName(schema.getAvroSchema());
        byte[] encoded = mapper.writer(schema).writeValueAsBytes(new NestedRecord("nested payload"));
        NestedRecord decoded = mapper.readerFor(NestedRecord.class).with(schema).readValue(encoded);

        assertThat(fullName).isEqualTo(NestedRecord.class.getName());
        assertThat(decoded.message).isEqualTo("nested payload");
    }

    public static class NestedRecord {
        public String message;

        public NestedRecord() {
        }

        public NestedRecord(String message) {
            this.message = message;
        }
    }
}
