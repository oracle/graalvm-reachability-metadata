/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Avro's schema fingerprint subsystem to key an application schema cache.
public class SchemaFingerprintTest {
    @Test
    @Timeout(60)
    public void identifiesEquivalentSchemasAndSeparatesDifferentVersions() {
        Schema firstVersion = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Shipment",
                  "namespace": "com.acme.shipping",
                  "doc": "The first published shipment contract.",
                  "fields": [
                    {"name": "trackingId", "type": "string"},
                    {"name": "weightGrams", "type": "long"}
                  ]
                }
                """);
        Schema sameContractWithUpdatedDocumentation = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Shipment",
                  "namespace": "com.acme.shipping",
                  "doc": "Documentation updated after publication.",
                  "fields": [
                    {"name": "trackingId", "type": "string"},
                    {"name": "weightGrams", "type": "long"}
                  ]
                }
                """);
        Schema secondVersion = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Shipment",
                  "namespace": "com.acme.shipping",
                  "fields": [
                    {"name": "trackingId", "type": "string"},
                    {"name": "weightGrams", "type": "long"},
                    {"name": "priority", "type": "boolean", "default": false}
                  ]
                }
                """);

        String canonicalSchema = SchemaNormalization.toParsingForm(firstVersion);
        long firstFingerprint = SchemaNormalization.parsingFingerprint64(firstVersion);
        long sameContractFingerprint = SchemaNormalization.parsingFingerprint64(
                sameContractWithUpdatedDocumentation);
        long secondFingerprint = SchemaNormalization.parsingFingerprint64(secondVersion);

        assertTrue(canonicalSchema.contains("com.acme.shipping.Shipment"));
        assertEquals(firstFingerprint, sameContractFingerprint);
        assertNotEquals(firstFingerprint, secondFingerprint);
    }
}
