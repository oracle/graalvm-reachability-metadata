/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.LongSerializationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's long-number serialization policy to exchange lossless checkout identifiers with a browser.
public class BrowserCheckoutIdentifiersTest {

    @Test
    @Timeout(60)
    public void confirmsACheckoutWithoutRoundingIdentifiersBeyondJavaScriptsSafeIntegerRange() {
        Gson gson = new GsonBuilder()
                .setLongSerializationPolicy(LongSerializationPolicy.STRING)
                .create();
        Checkout checkout = gson.fromJson("""
                {
                  "orderId": "9007199254740993",
                  "customerId": "9007199254740995",
                  "quantity": 3,
                  "unitPrice": 8.25
                }
                """, Checkout.class);

        assertEquals(9007199254740993L, checkout.orderId);
        assertEquals(9007199254740995L, checkout.customerId.longValue());
        assertEquals(3, checkout.quantity);
        assertEquals(new BigDecimal("8.25"), checkout.unitPrice);
        assertNull(checkout.receiptId);

        checkout.total = checkout.unitPrice.multiply(BigDecimal.valueOf(checkout.quantity));
        checkout.receiptId = 9223372036854775806L;
        checkout.confirmed = true;
        String response = gson.toJson(checkout);
        JsonObject browserReceipt = new JsonParser().parse(response).getAsJsonObject();

        // Browser clients must receive IDs as strings, not imprecise JavaScript numbers.
        assertTrue(browserReceipt.getAsJsonPrimitive("orderId").isString());
        assertTrue(browserReceipt.getAsJsonPrimitive("customerId").isString());
        assertTrue(browserReceipt.getAsJsonPrimitive("receiptId").isString());
        assertEquals("9007199254740993", browserReceipt.get("orderId").getAsString());
        assertEquals("9007199254740995", browserReceipt.get("customerId").getAsString());
        assertEquals("9223372036854775806", browserReceipt.get("receiptId").getAsString());
        assertTrue(browserReceipt.getAsJsonPrimitive("quantity").isNumber());
        assertTrue(browserReceipt.getAsJsonPrimitive("total").isNumber());
        assertEquals(new JsonParser().parse("""
                {
                  "orderId": "9007199254740993",
                  "customerId": "9007199254740995",
                  "quantity": 3,
                  "unitPrice": 8.25,
                  "receiptId": "9223372036854775806",
                  "total": 24.75,
                  "confirmed": true
                }
                """), browserReceipt);

        Checkout restored = gson.fromJson(response, Checkout.class);
        assertEquals(checkout.orderId, restored.orderId);
        assertEquals(checkout.customerId, restored.customerId);
        assertEquals(checkout.receiptId, restored.receiptId);
        assertEquals(new BigDecimal("24.75"), restored.total);
        assertTrue(restored.confirmed);
    }

    public static class Checkout {
        private long orderId;
        private Long customerId;
        private int quantity;
        private BigDecimal unitPrice;
        private Long receiptId;
        private BigDecimal total;
        private boolean confirmed;
    }
}
