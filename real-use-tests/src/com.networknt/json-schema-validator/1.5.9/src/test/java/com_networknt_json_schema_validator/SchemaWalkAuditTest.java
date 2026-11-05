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
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidationResult;
import com.networknt.schema.walk.JsonSchemaWalkListener;
import com.networknt.schema.walk.WalkEvent;
import com.networknt.schema.walk.WalkFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises schema-walk listeners for an application's validation audit trail.
public class SchemaWalkAuditTest {
    @Test
    @Timeout(60)
    public void recordsValidatedRequestProperties() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> visitedProperties = new ArrayList<>();
        JsonSchemaWalkListener auditListener = new AuditListener(visitedProperties);
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .propertyWalkListener(auditListener)
                .build();
        JsonSchema requestSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["requestId", "customer"],
                          "properties": {
                            "requestId": {
                              "type": "string",
                              "pattern": "^REQ-[0-9]{6}$"
                            },
                            "customer": {
                              "type": "object",
                              "required": ["name", "tier"],
                              "properties": {
                                "name": {"type": "string", "minLength": 1},
                                "tier": {"enum": ["standard", "priority"]}
                              },
                              "additionalProperties": false
                            }
                          },
                          "additionalProperties": false
                        }
                        """, config);
        JsonNode request = objectMapper.readTree("""
                {
                  "requestId": "REQ-204800",
                  "customer": {
                    "name": "Ada Lovelace",
                    "tier": "priority"
                  }
                }
                """);

        ValidationResult result = requestSchema.walk(request, true);

        assertTrue(result.getValidationMessages().isEmpty());
        assertEquals(List.of("/requestId", "/customer", "/customer/name", "/customer/tier"),
                visitedProperties);
    }

    private static final class AuditListener implements JsonSchemaWalkListener {
        private final List<String> visitedProperties;

        private AuditListener(List<String> visitedProperties) {
            this.visitedProperties = visitedProperties;
        }

        @Override
        public WalkFlow onWalkStart(WalkEvent event) {
            visitedProperties.add(event.getInstanceLocation().toString());
            return WalkFlow.CONTINUE;
        }

        @Override
        public void onWalkEnd(WalkEvent event, Set<ValidationMessage> messages) {
        }
    }
}
