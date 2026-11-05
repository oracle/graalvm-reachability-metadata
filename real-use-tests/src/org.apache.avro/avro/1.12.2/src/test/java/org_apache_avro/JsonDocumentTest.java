/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's JSON encoding subsystem to exchange a document with an application boundary.
public class JsonDocumentTest {
    @Test
    @Timeout(60)
    public void roundTripsAnOrderDocumentAsJson() throws IOException {
        Schema schema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "OrderDocument",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "status", "type": {"type": "enum", "name": "Status", "symbols": ["PENDING", "PAID"]}},
                    {"name": "note", "type": ["null", "string"], "default": null}
                  ]
                }
                """);
        GenericRecord order = new GenericData.Record(schema);
        order.put("orderId", "order-2048");
        order.put("status", new GenericData.EnumSymbol(schema.getField("status").schema(), "PAID"));
        order.put("note", "Invoice settled");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
        Encoder encoder = EncoderFactory.get().jsonEncoder(schema, output);
        writer.write(order, encoder);
        encoder.flush();

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        Decoder decoder = DecoderFactory.get().jsonDecoder(schema, new ByteArrayInputStream(output.toByteArray()));
        GenericRecord restored = reader.read(null, decoder);

        assertEquals("order-2048", restored.get("orderId").toString());
        assertEquals("PAID", restored.get("status").toString());
        assertEquals("Invoice settled", restored.get("note").toString());
    }
}
