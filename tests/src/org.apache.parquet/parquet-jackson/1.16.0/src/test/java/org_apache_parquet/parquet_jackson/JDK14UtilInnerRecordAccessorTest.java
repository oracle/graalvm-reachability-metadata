/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonProperty;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class JDK14UtilInnerRecordAccessorTest {
    @Test
    void serializesRecordComponentsByName() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final InventoryItem item = new InventoryItem("book", 3);

        final String json = mapper.writeValueAsString(item);

        assertThat(json).contains("\"label\":\"book\"");
        assertThat(json).contains("\"quantity\":3");
    }

    @Test
    void deserializesRecordUsingCanonicalConstructor() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final InventoryItem item = mapper.readValue("""
                {
                  "label": "pencil",
                  "quantity": 12
                }
                """, InventoryItem.class);

        assertThat(item).isEqualTo(new InventoryItem("pencil", 12));
    }

    public record InventoryItem(@JsonProperty("label") String name, int quantity) {
    }
}
