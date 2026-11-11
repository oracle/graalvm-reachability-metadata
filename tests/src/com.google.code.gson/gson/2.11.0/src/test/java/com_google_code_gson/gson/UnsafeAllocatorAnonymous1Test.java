/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

public class UnsafeAllocatorAnonymous1Test {
    @Test
    void deserializesImmutableValueWithoutNoArgsConstructor() {
        Gson gson = new Gson();
        String json = """
                {"sku":"stock-7","quantity":9}
                """;

        Stock stock = gson.fromJson(json, Stock.class);

        assertThat(stock.sku).isEqualTo("stock-7");
        assertThat(stock.quantity).isEqualTo(9);
        assertThat(JsonParser.parseString(gson.toJson(stock))).isEqualTo(JsonParser.parseString(json));
    }

    private static final class Stock {
        private final String sku;
        private final int quantity;

        private Stock(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
