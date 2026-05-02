/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveTypeAdapterFactoryAnonymous1Test {
    private final Gson gson = new Gson();

    @Test
    public void serializesDeclaredFieldsWithTheReflectiveTypeAdapter() {
        final InventoryItem item = new InventoryItem("pencil", 7, true, new Dimensions(2, 10));

        final String json = gson.toJson(item);

        assertThat(json).contains(
                "\"sku\":\"pencil\"",
                "\"quantity\":7",
                "\"available\":true",
                "\"dimensions\"",
                "\"width\":2",
                "\"height\":10"
        );
    }

    @Test
    public void deserializesDeclaredFieldsWithTheReflectiveTypeAdapter() {
        final String json = """
                {
                  "sku": "notebook",
                  "quantity": 12,
                  "available": false,
                  "dimensions": {
                    "width": 5,
                    "height": 8
                  }
                }
                """;

        final InventoryItem item = gson.fromJson(json, InventoryItem.class);

        assertThat(item.sku).isEqualTo("notebook");
        assertThat(item.quantity).isEqualTo(12);
        assertThat(item.available).isFalse();
        assertThat(item.dimensions.width).isEqualTo(5);
        assertThat(item.dimensions.height).isEqualTo(8);
    }

    public static final class InventoryItem {
        String sku;
        int quantity;
        boolean available;
        Dimensions dimensions;

        public InventoryItem() {
        }

        InventoryItem(final String sku, final int quantity, final boolean available,
                final Dimensions dimensions) {
            this.sku = sku;
            this.quantity = quantity;
            this.available = available;
            this.dimensions = dimensions;
        }
    }

    public static final class Dimensions {
        int width;
        int height;

        public Dimensions() {
        }

        Dimensions(final int width, final int height) {
            this.width = width;
            this.height = height;
        }
    }
}
