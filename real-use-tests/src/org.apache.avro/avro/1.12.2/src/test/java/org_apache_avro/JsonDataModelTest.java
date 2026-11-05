/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro;

import org.apache.avro.data.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises Avro's JSON data model subsystem to normalize a JSON extension received by an application.
public class JsonDataModelTest {
    @Test
    @Timeout(60)
    public void normalizesAnOrderExtensionWithoutLosingNestedValues() {
        Object parsed = Json.parseJson("""
                {
                  "orderId": "order-2048",
                  "items": [
                    {"sku": "SKU-1", "quantity": 2}
                  ],
                  "paid": true
                }
                """);

        Map<?, ?> document = assertInstanceOf(Map.class, parsed);
        List<?> items = assertInstanceOf(List.class, document.get("items"));
        Map<?, ?> firstItem = assertInstanceOf(Map.class, items.get(0));

        assertEquals("order-2048", document.get("orderId"));
        assertEquals(1, items.size());
        assertEquals("SKU-1", firstItem.get("sku"));
        assertEquals(2, firstItem.get("quantity"));
        assertEquals(Boolean.TRUE, document.get("paid"));
        assertEquals(
                "{\"orderId\":\"order-2048\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2}],\"paid\":true}",
                Json.toString(parsed));
    }
}
