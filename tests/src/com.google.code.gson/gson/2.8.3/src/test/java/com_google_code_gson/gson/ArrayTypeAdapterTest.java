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

public class ArrayTypeAdapterTest {
    private final Gson gson = new Gson();

    @Test
    void deserializesObjectArrayThroughReflectiveArrayAllocation() {
        String json = """
                [
                  {"name":"paperclips","quantity":3,"available":true},
                  {"name":"folders","quantity":9,"available":false}
                ]
                """;

        InventoryItem[] items = gson.fromJson(json, InventoryItem[].class);

        assertThat(items).hasSize(2);
        assertThat(items[0].name).isEqualTo("paperclips");
        assertThat(items[0].quantity).isEqualTo(3);
        assertThat(items[0].available).isTrue();
        assertThat(items[1].name).isEqualTo("folders");
        assertThat(items[1].quantity).isEqualTo(9);
        assertThat(items[1].available).isFalse();
    }

    public static final class InventoryItem {
        private String name;
        private int quantity;
        private boolean available;

        public InventoryItem() {
        }
    }
}
