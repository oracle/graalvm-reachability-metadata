/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Avro's schema compatibility subsystem to validate an event schema before deployment.
public class SchemaEvolutionCompatibilityTest {
    @Test
    @Timeout(60)
    public void acceptsACompatibleEventChangeAndRejectsABreakingOne() {
        Schema publishedOrder = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "OrderEvent",
                  "namespace": "com.acme.orders",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "totalCents", "type": "long"}
                  ]
                }
                """);
        Schema compatibleConsumer = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "OrderEvent",
                  "namespace": "com.acme.orders",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "totalCents", "type": "long"},
                    {"name": "currency", "type": "string", "default": "USD"}
                  ]
                }
                """);
        Schema breakingConsumer = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "OrderEvent",
                  "namespace": "com.acme.orders",
                  "fields": [
                    {"name": "orderId", "type": "string"},
                    {"name": "totalCents", "type": "string"}
                  ]
                }
                """);

        SchemaCompatibility.SchemaPairCompatibility compatible =
                SchemaCompatibility.checkReaderWriterCompatibility(compatibleConsumer, publishedOrder);
        SchemaCompatibility.SchemaPairCompatibility breaking =
                SchemaCompatibility.checkReaderWriterCompatibility(breakingConsumer, publishedOrder);

        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE, compatible.getType());
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE, breaking.getType());
        assertEquals(1, breaking.getResult().getIncompatibilities().size());
        assertEquals(SchemaCompatibility.SchemaIncompatibilityType.TYPE_MISMATCH,
                breaking.getResult().getIncompatibilities().get(0).getType());
    }
}
