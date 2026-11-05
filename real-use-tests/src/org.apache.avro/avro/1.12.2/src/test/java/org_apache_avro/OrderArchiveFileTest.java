/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
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

/// Exercises Avro's object container file subsystem to archive orders and read them with an evolved schema.
public class OrderArchiveFileTest {
    @Test
    @Timeout(60)
    public void readsArchivedOrdersWithACompatibleSchema() throws IOException {
        Schema writerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Order",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "customer", "type": "string"},
                    {"name": "totalCents", "type": "long"}
                  ]
                }
                """);
        Schema readerSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Order",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "customer", "type": "string"},
                    {"name": "totalCents", "type": "long"},
                    {"name": "currency", "type": "string", "default": "USD"}
                  ]
                }
                """);
        GenericRecord order = new GenericData.Record(writerSchema);
        order.put("orderId", "order-1042");
        order.put("customer", "Acme Supplies");
        order.put("totalCents", 12550L);

        Path archive = Files.createTempFile("orders-", ".avro");
        try {
            try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(
                    new GenericDatumWriter<>(writerSchema))) {
                writer.setCodec(CodecFactory.deflateCodec(6));
                writer.create(writerSchema, archive.toFile());
                writer.append(order);
            }

            try (DataFileReader<GenericRecord> reader = new DataFileReader<>(archive.toFile(),
                    new GenericDatumReader<>(writerSchema, readerSchema))) {
                assertEquals(true, reader.hasNext());
                GenericRecord archivedOrder = reader.next();
                assertEquals("order-1042", archivedOrder.get("orderId").toString());
                assertEquals("Acme Supplies", archivedOrder.get("customer").toString());
                assertEquals(12550L, archivedOrder.get("totalCents"));
                assertEquals("USD", archivedOrder.get("currency").toString());
                assertFalse(reader.hasNext());
            }
        } finally {
            Files.deleteIfExists(archive);
        }
    }
}
