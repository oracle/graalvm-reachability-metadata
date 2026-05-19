/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericDatumReaderInnerReaderCacheTest {
    @Test
    void createsCustomStringRepresentationThroughCachedStringConstructor() throws IOException {
        Schema schema = Schema.create(Schema.Type.STRING);
        GenericData data = new GenericData().setFastReaderEnabled(false);
        GenericDatumReader<Object> reader = new StringTokenDatumReader(schema, data);
        byte[] encoded = encodeStrings("first", "second");
        Decoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);

        Object first = reader.read(null, decoder);
        Object second = reader.read(null, decoder);

        assertThat(first).isEqualTo(new StringToken("first"));
        assertThat(second).isEqualTo(new StringToken("second"));
    }

    private static byte[] encodeStrings(String first, String second) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        encoder.writeString(first);
        encoder.writeString(second);
        encoder.flush();
        return output.toByteArray();
    }

    private static final class StringTokenDatumReader extends GenericDatumReader<Object> {
        private StringTokenDatumReader(Schema schema, GenericData data) {
            super(schema, schema, data);
        }

        @Override
        protected Class<?> findStringClass(Schema schema) {
            return StringToken.class;
        }
    }

    public static final class StringToken {
        private final String value;

        public StringToken(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringToken)) {
                return false;
            }
            StringToken that = (StringToken) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
