/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt_json_schema_validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.output.OutputUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises hierarchical validation output for an application's invoice API.
public class InvoiceValidationOutputTest {
    @Test
    @Timeout(60)
    public void returnsStructuredLocationsForAnInvalidInvoice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema invoiceSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "$id": "https://schemas.example.test/invoice.json",
                          "type": "object",
                          "required": ["invoiceId", "currency", "total"],
                          "properties": {
                            "invoiceId": {
                              "type": "string",
                              "pattern": "^INV-[0-9]{6}$"
                            },
                            "currency": {
                              "enum": ["USD", "EUR"]
                            },
                            "total": {
                              "type": "number",
                              "exclusiveMinimum": 0
                            }
                          },
                          "additionalProperties": false
                        }
                        """);
        JsonNode invalidInvoice = objectMapper.readTree("""
                {
                  "invoiceId": "INV-42",
                  "currency": "GBP",
                  "total": 0
                }
                """);

        OutputUnit output = invoiceSchema.validate(invalidInvoice, OutputFormat.HIERARCHICAL);
        List<String> invalidLocations = new ArrayList<>();
        collectInvalidLocations(output, invalidLocations);

        assertFalse(output.isValid());
        assertTrue(invalidLocations.contains("$.invoiceId"));
        assertTrue(invalidLocations.contains("$.currency"));
        assertTrue(invalidLocations.contains("$.total"));
    }

    @Test
    @Timeout(60)
    public void marksAValidInvoiceAsValid() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema invoiceSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["invoiceId", "currency", "total"],
                          "properties": {
                            "invoiceId": {"type": "string", "pattern": "^INV-[0-9]{6}$"},
                            "currency": {"enum": ["USD", "EUR"]},
                            "total": {"type": "number", "exclusiveMinimum": 0}
                          },
                          "additionalProperties": false
                        }
                        """);

        OutputUnit output = invoiceSchema.validate(objectMapper.readTree("""
                {
                  "invoiceId": "INV-000042",
                  "currency": "USD",
                  "total": 125.50
                }
                """), OutputFormat.HIERARCHICAL);

        assertTrue(output.isValid());
    }

    private static void collectInvalidLocations(OutputUnit output, List<String> locations) {
        if (!output.isValid() && output.getInstanceLocation() != null) {
            locations.add(output.getInstanceLocation());
        }
        if (output.getDetails() != null) {
            for (OutputUnit detail : output.getDetails()) {
                collectInvalidLocations(detail, locations);
            }
        }
    }
}
