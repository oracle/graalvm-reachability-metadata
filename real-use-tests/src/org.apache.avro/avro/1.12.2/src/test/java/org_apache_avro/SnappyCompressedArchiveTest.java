/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileConstants;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Avro's Snappy codec subsystem to archive and restore an application event.
public class SnappyCompressedArchiveTest {
    @Test
    @Timeout(60)
    public void restoresEventsFromASnappyCompressedArchive() throws IOException {
        Schema eventSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "ShipmentEvent",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "shipmentId", "type": "string"},
                    {"name": "status", "type": "string"},
                    {"name": "attempt", "type": "int"}
                  ]
                }
                """);
        GenericRecord event = new GenericData.Record(eventSchema);
        event.put("shipmentId", "shipment-2048");
        event.put("status", "DISPATCHED");
        event.put("attempt", 2);

        Path archive = Files.createTempFile("shipment-events-", ".avro");
        try {
            try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(
                    new GenericDatumWriter<>(eventSchema))) {
                writer.setCodec(CodecFactory.snappyCodec());
                writer.create(eventSchema, archive.toFile());
                writer.append(event);
            }

            try (DataFileReader<GenericRecord> reader = new DataFileReader<>(archive.toFile(),
                    new GenericDatumReader<>(eventSchema))) {
                assertEquals(DataFileConstants.SNAPPY_CODEC,
                        reader.getMetaString(DataFileConstants.CODEC));
                assertEquals(true, reader.hasNext());
                GenericRecord restored = reader.next();
                assertEquals("shipment-2048", restored.get("shipmentId").toString());
                assertEquals("DISPATCHED", restored.get("status").toString());
                assertEquals(2, restored.get("attempt"));
                assertFalse(reader.hasNext());
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
