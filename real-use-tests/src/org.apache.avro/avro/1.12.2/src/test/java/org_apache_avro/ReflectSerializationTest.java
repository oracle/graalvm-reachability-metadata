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
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's reflective data model subsystem to serialize and restore an application record.
public class ReflectSerializationTest {
    @Test
    @Timeout(60)
    public void roundTripsAnInvoiceThroughBinaryAvro() throws IOException {
        Invoice invoice = new Invoice("invoice-1042", "Acme Supplies", 12550L);
        System.setProperty("org.apache.avro.SERIALIZABLE_PACKAGES", "org_apache_avro");
        Schema invoiceSchema = ReflectData.get().getSchema(Invoice.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ReflectDatumWriter<Invoice> writer = new ReflectDatumWriter<>(invoiceSchema);
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        writer.write(invoice, encoder);
        encoder.flush();

        ReflectDatumReader<Invoice> reader = new ReflectDatumReader<>(invoiceSchema);
        Decoder decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        Invoice restored = reader.read(null, decoder);

        assertEquals("invoice-1042", restored.invoiceId);
        assertEquals("Acme Supplies", restored.customer);
        assertEquals(12550L, restored.totalCents);
    }

    public static class Invoice {
        public String invoiceId;
        public String customer;
        public long totalCents;

        public Invoice() {
        }

        public Invoice(String invoiceId, String customer, long totalCents) {
            this.invoiceId = invoiceId;
            this.customer = customer;
            this.totalCents = totalCents;
        }
    }
}
