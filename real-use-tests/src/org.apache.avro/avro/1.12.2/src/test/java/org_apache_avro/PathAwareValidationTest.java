/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Avro's path-aware validation subsystem to diagnose an invalid nested application record.
public class PathAwareValidationTest {
    @Test
    @Timeout(60)
    public void reportsTheLocationOfAnInvalidLineItem() throws IOException {
        Schema checkoutSchema = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Checkout",
                  "namespace": "com.acme.orders",
                  "fields": [
                    {"name": "checkoutId", "type": "string"},
                    {"name": "lineItems", "type": {
                      "type": "array",
                      "items": {
                        "type": "record",
                        "name": "LineItem",
                        "fields": [
                          {"name": "sku", "type": "string"},
                          {"name": "quantity", "type": "int"}
                        ]
                      }
                    }}
                  ]
                }
                """);
        Schema lineItemSchema = checkoutSchema.getField("lineItems").schema().getElementType();

        GenericRecord firstItem = new GenericData.Record(lineItemSchema);
        firstItem.put("sku", "SKU-1");
        firstItem.put("quantity", 2);
        GenericRecord invalidItem = new GenericData.Record(lineItemSchema);
        invalidItem.put("sku", "SKU-2");
        invalidItem.put("quantity", "two");

        GenericRecord checkout = new GenericData.Record(checkoutSchema);
        checkout.put("checkoutId", "checkout-1042");
        checkout.put("lineItems", new GenericData.Array<>(
                checkoutSchema.getField("lineItems").schema(), List.of(firstItem, invalidItem)));

        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(checkoutSchema);
        Encoder encoder = EncoderFactory.get().binaryEncoder(new ByteArrayOutputStream(), null);
        ClassCastException failure = assertThrows(
                ClassCastException.class, () -> writer.write(checkout, encoder));

        assertEquals("value two (a java.lang.String) cannot be cast to expected type int "
                        + "at Checkout.lineItems[1].quantity", failure.getMessage());
    }
}
