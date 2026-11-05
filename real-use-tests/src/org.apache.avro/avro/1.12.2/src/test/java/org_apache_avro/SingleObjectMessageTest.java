/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's single-object message encoding subsystem to exchange a versioned event.
public class SingleObjectMessageTest {
    @Test
    @Timeout(60)
    public void decodesAnEventWithTheConsumerSchema() throws IOException {
        Schema writerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "ShipmentCreated",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "shipmentId", "type": "string"},
                    {"name": "destination", "type": "string"}
                  ]
                }
                """);
        Schema readerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "ShipmentCreated",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "shipmentId", "type": "string"},
                    {"name": "destination", "type": "string"},
                    {"name": "priority", "type": "string", "default": "STANDARD"}
                  ]
                }
                """);
        GenericRecord event = new GenericRecordBuilder(writerSchema)
                .set("shipmentId", "shipment-1042")
                .set("destination", "Berlin")
                .build();

        ByteArrayOutputStream wireMessage = new ByteArrayOutputStream();
        BinaryMessageEncoder<GenericRecord> encoder = new BinaryMessageEncoder<>(
                GenericData.get(), writerSchema);
        encoder.encode(event, wireMessage);

        SchemaStore.Cache schemaStore = new SchemaStore.Cache();
        schemaStore.addSchema(writerSchema);
        BinaryMessageDecoder<GenericRecord> decoder = new BinaryMessageDecoder<>(
                GenericData.get(), readerSchema, schemaStore);
        GenericRecord decoded = decoder.decode(
                new ByteArrayInputStream(wireMessage.toByteArray()), null);

        assertEquals("shipment-1042", decoded.get("shipmentId").toString());
        assertEquals("Berlin", decoded.get("destination").toString());
        assertEquals("STANDARD", decoded.get("priority").toString());
    }
}
