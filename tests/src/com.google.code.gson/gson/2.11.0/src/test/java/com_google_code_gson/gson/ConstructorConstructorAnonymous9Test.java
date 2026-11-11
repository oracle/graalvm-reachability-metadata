/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ConstructorConstructorAnonymous9Test {
    @Test
    void invokesNoArgsConstructorBeforePopulatingJsonFields() {
        Gson gson = new Gson();

        Cart cart = gson.fromJson("""
                {"owner":"shopper","items":["tea","rice"]}
                """, Cart.class);

        assertThat(cart.owner).isEqualTo("shopper");
        assertThat(cart.items).containsExactly("tea", "rice");
        assertThat(cart.currency).isEqualTo("EUR");
        assertThat(cart.events).containsExactly("created");
    }

    private static final class Cart {
        private String owner;
        private List<String> items;
        private String currency;
        private final transient List<String> events;

        private Cart() {
            items = new ArrayList<>();
            currency = "EUR";
            events = new ArrayList<>();
            events.add("created");
        }
    }
}
