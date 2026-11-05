/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Avro's schema builder subsystem to create and exchange a shipment contract.
public class SchemaBuilderShipmentTest {
    @Test
    @Timeout(60)
    public void buildsAndUsesAShipmentSchema() throws IOException {
        Schema shipmentSchema = SchemaBuilder.record("Shipment")
                .namespace("com.acme.shipping")
                .fields()
                .requiredString("trackingId")
                .requiredLong("weightGrams")
                .name("priority")
                .type()
                .enumeration("Priority")
                .symbols("STANDARD", "EXPRESS")
                .enumDefault("STANDARD")
                .name("insured")
                .type()
                .booleanType()
                .booleanDefault(false)
                .endRecord();

        GenericRecord shipment = new GenericRecordBuilder(shipmentSchema)
                .set("trackingId", "trk-2048")
                .set("weightGrams", 4200L)
                .set("priority", new GenericData.EnumSymbol(
                        shipmentSchema.getField("priority").schema(), "EXPRESS"))
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        new GenericDatumWriter<GenericRecord>(shipmentSchema).write(shipment, encoder);
        encoder.flush();

        Decoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(output.toByteArray()), null);
        GenericRecord restored = new GenericDatumReader<GenericRecord>(shipmentSchema)
                .read(null, decoder);

        assertEquals("com.acme.shipping.Shipment", shipmentSchema.getFullName());
        assertEquals(4, shipmentSchema.getFields().size());
        assertEquals("trk-2048", restored.get("trackingId").toString());
        assertEquals(4200L, restored.get("weightGrams"));
        assertEquals("EXPRESS", restored.get("priority").toString());
        assertFalse((Boolean) restored.get("insured"));
        assertTrue(shipmentSchema.getField("insured").hasDefaultValue());
    }
}
