/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises Jsoup's HTML form parsing subsystem to collect a checkout submission from a page.
 */
public class CheckoutFormExtractionTest {

    @Test
    @Timeout(60)
    public void extractsTheSubmittedCheckoutFields() {
        String checkoutPage = """
                <!doctype html>
                <html>
                <head><title>Checkout</title></head>
                <body>
                <form id="checkout" action="/orders" method="post">
                  <input type="hidden" name="cartToken" value="cart-42">
                  <input type="email" name="email" value="buyer@example.test">
                  <select name="shipping">
                    <option value="standard">Standard delivery</option>
                    <option value="express" selected>Express delivery</option>
                  </select>
                  <input type="checkbox" name="giftWrap" value="yes" checked>
                  <input type="checkbox" name="marketing" value="yes">
                  <textarea name="note">Leave at the desk</textarea>
                  <button type="submit">Place order</button>
                </form>
                </body>
                </html>
                """;

        Document page = Jsoup.parse(checkoutPage, "https://shop.example.test/checkout");
        FormElement checkout = page.expectForm("#checkout");
        List<Connection.KeyVal> fields = checkout.formData();
        Map<String, List<String>> submitted = fields.stream().collect(Collectors.groupingBy(
                Connection.KeyVal::key,
                LinkedHashMap::new,
                Collectors.mapping(Connection.KeyVal::value, Collectors.toList())));

        assertEquals("Checkout", page.title());
        assertEquals("/orders", checkout.attr("action"));
        assertEquals(List.of("cart-42"), submitted.get("cartToken"));
        assertEquals(List.of("buyer@example.test"), submitted.get("email"));
        assertEquals(List.of("express"), submitted.get("shipping"));
        assertEquals(List.of("yes"), submitted.get("giftWrap"));
        assertEquals(List.of("Leave at the desk"), submitted.get("note"));
        assertFalse(submitted.containsKey("marketing"));
    }
}
