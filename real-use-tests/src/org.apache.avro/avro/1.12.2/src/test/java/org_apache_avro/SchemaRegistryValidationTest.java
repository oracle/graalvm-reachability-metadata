/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Avro's schema validation subsystem to gate schema versions before publishing them.
public class SchemaRegistryValidationTest {
    @Test
    @Timeout(60)
    public void acceptsCompatibleVersionsAndRejectsBreakingVersions() {
        Schema versionOne = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "InventoryChanged",
                  "namespace": "com.acme.inventory",
                  "fields": [
                    {"name": "itemId", "type": "string"},
                    {"name": "quantity", "type": "int"}
                  ]
                }
                """);
        Schema compatibleVersion = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "InventoryChanged",
                  "namespace": "com.acme.inventory",
                  "fields": [
                    {"name": "itemId", "type": "string"},
                    {"name": "quantity", "type": "int"},
                    {"name": "warehouse", "type": "string", "default": "central"}
                  ]
                }
                """);
        Schema breakingVersion = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "InventoryChanged",
                  "namespace": "com.acme.inventory",
                  "fields": [
                    {"name": "itemId", "type": "string"},
                    {"name": "quantity", "type": "string"}
                  ]
                }
                """);

        SchemaValidator validator = new SchemaValidatorBuilder()
                .canReadStrategy()
                .validateAll();

        assertDoesNotThrow(() -> validator.validate(compatibleVersion, List.of(versionOne)));
        assertThrows(
                SchemaValidationException.class,
                () -> validator.validate(breakingVersion, List.of(versionOne)));
    }
}
