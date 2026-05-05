/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryAnonymous1Test {
    private final Gson gson = new Gson();

    @Test
    void serializesObjectFieldsThroughReflectiveBoundFields() {
        InventoryItem item = new InventoryItem("desk-lamp", 12, true);

        String json = gson.toJson(item);

        assertThat(json).contains("\"name\":\"desk-lamp\"");
        assertThat(json).contains("\"quantity\":12");
        assertThat(json).contains("\"available\":true");
    }

    @Test
    void deserializesObjectFieldsThroughReflectiveBoundFields() {
        String json = "{\"name\":\"notebook\",\"quantity\":5,\"available\":false}";

        InventoryItem item = gson.fromJson(json, InventoryItem.class);

        assertThat(item.name).isEqualTo("notebook");
        assertThat(item.quantity).isEqualTo(5);
        assertThat(item.available).isFalse();
    }

    public static final class InventoryItem {
        private String name;
        private int quantity;
        private boolean available;

        public InventoryItem() {
        }

        InventoryItem(String name, int quantity, boolean available) {
            this.name = name;
            this.quantity = quantity;
            this.available = available;
        }
    }
}
