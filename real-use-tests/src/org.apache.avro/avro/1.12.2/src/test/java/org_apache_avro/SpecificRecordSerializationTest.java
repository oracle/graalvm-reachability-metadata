/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's generated specific-record subsystem to serialize and restore an application event.
public class SpecificRecordSerializationTest {
    @Test
    @Timeout(60)
    public void roundTripsAShipmentEventThroughBinaryAvro() throws IOException {
        ShipmentCreated event = new ShipmentCreated("shipment-1042", "Berlin", 1750L);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SpecificDatumWriter<ShipmentCreated> writer = new SpecificDatumWriter<>(ShipmentCreated.SCHEMA$);
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        writer.write(event, encoder);
        encoder.flush();

        SpecificDatumReader<ShipmentCreated> reader = new SpecificDatumReader<>(ShipmentCreated.SCHEMA$);
        Decoder decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        ShipmentCreated restored = reader.read(new ShipmentCreated(), decoder);

        assertEquals("shipment-1042", restored.getShipmentId().toString());
        assertEquals("Berlin", restored.getDestination().toString());
        assertEquals(1750L, restored.getWeightGrams());
    }

    public static class ShipmentCreated extends SpecificRecordBase {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "ShipmentCreated",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "shipmentId", "type": "string"},
                    {"name": "destination", "type": "string"},
                    {"name": "weightGrams", "type": "long"}
                  ]
                }
                """);

        private CharSequence shipmentId;
        private CharSequence destination;
        private long weightGrams;

        public ShipmentCreated() {
        }

        public ShipmentCreated(CharSequence shipmentId, CharSequence destination, long weightGrams) {
            this.shipmentId = shipmentId;
            this.destination = destination;
            this.weightGrams = weightGrams;
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            return switch (field) {
                case 0 -> shipmentId;
                case 1 -> destination;
                case 2 -> weightGrams;
                default -> throw new IndexOutOfBoundsException("Invalid field index: " + field);
            };
        }

        @Override
        public void put(int field, Object value) {
            switch (field) {
                case 0 -> shipmentId = (CharSequence) value;
                case 1 -> destination = (CharSequence) value;
                case 2 -> weightGrams = (Long) value;
                default -> throw new IndexOutOfBoundsException("Invalid field index: " + field);
            }
        }

        public CharSequence getShipmentId() {
            return shipmentId;
        }

        public CharSequence getDestination() {
            return destination;
        }

        public long getWeightGrams() {
            return weightGrams;
        }
    }
}
