/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordsHelpersDynamicAccessTest {
    private static final JSON JSON_WITH_RECORD_DECLARATION_ORDER = JSON.std.with(
            JSON.Feature.WRITE_RECORD_FIELDS_IN_DECLARATION_ORDER);

    @Test
    void deserializesRecordThroughCanonicalConstructor() throws Exception {
        DeserializableInventoryItem item = JSON.std.beanFrom(DeserializableInventoryItem.class, """
                {
                  "quantity": 4,
                  "stocked": true,
                  "sku": "SKU-13"
                }
                """);

        assertThat(item.sku()).isEqualTo("SKU-13");
        assertThat(item.quantity()).isEqualTo(4);
        assertThat(item.stocked()).isTrue();
        assertThat(item.getDisplayName()).isEqualTo("SKU-13 x 4");
    }

    @Test
    void serializesRecordInDeclarationOrder() throws Exception {
        InventoryItem item = new InventoryItem("SKU-21", 7, false);

        String json = JSON_WITH_RECORD_DECLARATION_ORDER.asString(item);

        assertThat(json).isEqualTo("{\"sku\":\"SKU-21\",\"quantity\":7,\"stocked\":false}");
    }

    public record DeserializableInventoryItem(String sku, int quantity, boolean stocked) {
        public String getDisplayName() {
            return sku + " x " + quantity;
        }
    }

    public record InventoryItem(String sku, int quantity, boolean stocked) {
    }
}
