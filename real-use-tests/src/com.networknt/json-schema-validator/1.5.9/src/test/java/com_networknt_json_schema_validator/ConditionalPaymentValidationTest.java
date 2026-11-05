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
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises conditional schema validation for an application's payment method API.
public class ConditionalPaymentValidationTest {
    @Test
    @Timeout(60)
    public void validatesPaymentFieldsBasedOnMethod() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSchema paymentSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema("""
                        {
                          "type": "object",
                          "required": ["paymentId", "method", "amount"],
                          "properties": {
                            "paymentId": {"type": "string", "pattern": "^PAY-[0-9]{6}$"},
                            "method": {"enum": ["card", "bank"]},
                            "amount": {"type": "number", "exclusiveMinimum": 0},
                            "cardNumber": {"type": "string", "pattern": "^[0-9]{16}$"},
                            "accountNumber": {"type": "string", "pattern": "^[0-9]{10}$"}
                          },
                          "allOf": [
                            {
                              "if": {
                                "properties": {"method": {"const": "card"}},
                                "required": ["method"]
                              },
                              "then": {"required": ["cardNumber"]}
                            },
                            {
                              "if": {
                                "properties": {"method": {"const": "bank"}},
                                "required": ["method"]
                              },
                              "then": {"required": ["accountNumber"]}
                            }
                          ],
                          "additionalProperties": false
                        }
                        """);

        JsonNode cardPayment = objectMapper.readTree("""
                {
                  "paymentId": "PAY-000042",
                  "method": "card",
                  "amount": 49.95,
                  "cardNumber": "4111111111111111"
                }
                """);
        JsonNode bankPayment = objectMapper.readTree("""
                {
                  "paymentId": "PAY-000043",
                  "method": "bank",
                  "amount": 125.00,
                  "accountNumber": "1234567890"
                }
                """);
        JsonNode incompleteBankPayment = objectMapper.readTree("""
                {
                  "paymentId": "PAY-000044",
                  "method": "bank",
                  "amount": 125.00
                }
                """);

        Set<ValidationMessage> cardMessages = paymentSchema.validate(cardPayment);
        Set<ValidationMessage> bankMessages = paymentSchema.validate(bankPayment);
        Set<ValidationMessage> incompleteMessages = paymentSchema.validate(incompleteBankPayment);
        Set<String> incompleteLocations = incompleteMessages.stream()
                .map(message -> message.getInstanceLocation().toString())
                .collect(Collectors.toSet());

        assertTrue(cardMessages.isEmpty());
        assertTrue(bankMessages.isEmpty());
        assertEquals(Set.of("$"), incompleteLocations);
    }
}
