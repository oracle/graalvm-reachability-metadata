/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.data.TimeConversions;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's logical type subsystem to persist a payment timestamp and amount in a generic record.
public class LogicalPaymentTest {
    @Test
    @Timeout(60)
    public void roundTripsApplicationValuesThroughLogicalTypes() throws IOException {
        Schema timestampSchema = LogicalTypes.timestampMillis()
                .addToSchema(Schema.create(Schema.Type.LONG));
        Schema amountSchema = LogicalTypes.decimal(9, 2)
                .addToSchema(Schema.create(Schema.Type.BYTES));
        Schema paymentSchema = SchemaBuilder.record("Payment").fields()
                .name("paymentId").type().stringType().noDefault()
                .name("capturedAt").type(timestampSchema).noDefault()
                .name("amount").type(amountSchema).noDefault()
                .endRecord();

        GenericData model = new GenericData();
        model.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
        model.addLogicalTypeConversion(new Conversions.DecimalConversion());

        GenericRecord payment = new GenericData.Record(paymentSchema);
        payment.put("paymentId", "payment-1042");
        payment.put("capturedAt", Instant.parse("2025-06-15T12:34:56.789Z"));
        payment.put("amount", new BigDecimal("125.50"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(paymentSchema, model);
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        writer.write(payment, encoder);
        encoder.flush();

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(paymentSchema, paymentSchema, model);
        Decoder decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        GenericRecord restored = reader.read(null, decoder);

        assertEquals("payment-1042", restored.get("paymentId").toString());
        assertEquals(Instant.parse("2025-06-15T12:34:56.789Z"), restored.get("capturedAt"));
        assertEquals(new BigDecimal("125.50"), restored.get("amount"));
    }
}
