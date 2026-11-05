/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.FastReaderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's fast reader subsystem to consume an evolved shipment record efficiently.
public class FastReaderShipmentTest {
    @Test
    @Timeout(60)
    public void readsAnEncodedShipmentWithTheFastReader() throws IOException {
        Schema writerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Shipment",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "trackingId", "type": "string"},
                    {"name": "weightGrams", "type": "long"}
                  ]
                }
                """);
        Schema readerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Shipment",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "trackingId", "type": "string"},
                    {"name": "weightGrams", "type": "long"},
                    {"name": "priority", "type": "string", "default": "STANDARD"}
                  ]
                }
                """);
        GenericRecord shipment = new GenericRecordBuilder(writerSchema)
                .set("trackingId", "TRACK-2048")
                .set("weightGrams", 1750L)
                .build();

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(encoded, null);
        new GenericDatumWriter<GenericRecord>(writerSchema).write(shipment, encoder);
        encoder.flush();

        DatumReader<GenericRecord> reader = FastReaderBuilder.get()
                .createDatumReader(writerSchema, readerSchema);
        Decoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(encoded.toByteArray()), null);
        GenericRecord decoded = reader.read(null, decoder);

        assertEquals("TRACK-2048", decoded.get("trackingId").toString());
        assertEquals(1750L, decoded.get("weightGrams"));
        assertEquals("STANDARD", decoded.get("priority").toString());
    }
}
