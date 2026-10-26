/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Gson's schema-versioning subsystem to exchange delivery quotes with legacy and current clients.
public class DeliveryQuoteVersioningTest {

    @Test
    @Timeout(60)
    public void acceptsAndPublishesOnlyTheFieldsInEachClientsSchema() {
        Gson legacyClient = new GsonBuilder().setVersion(1.5).create();
        Gson currentClient = new GsonBuilder().setVersion(2.0).create();
        String request = """
                {
                  "orderId": "O-318",
                  "shippingDays": 5,
                  "deliveryWindow": {"minimumDays": 2, "maximumDays": 4},
                  "trackingAvailable": true
                }
                """;

        DeliveryQuote legacy = legacyClient.fromJson(request, DeliveryQuote.class);
        assertEquals("O-318", legacy.orderId);
        assertEquals(5, legacy.shippingDays);
        assertNull(legacy.deliveryWindow);
        assertNull(legacy.trackingAvailable);

        DeliveryQuote current = currentClient.fromJson(request, DeliveryQuote.class);
        assertEquals("O-318", current.orderId);
        assertEquals(0, current.shippingDays);
        assertEquals(2, current.deliveryWindow.minimumDays);
        assertEquals(4, current.deliveryWindow.maximumDays);
        assertEquals(Boolean.TRUE, current.trackingAvailable);

        // The service maintains both representations while clients migrate to the new schema.
        DeliveryQuote quote = new DeliveryQuote();
        quote.orderId = current.orderId;
        quote.shippingDays = current.deliveryWindow.maximumDays;
        quote.deliveryWindow = current.deliveryWindow;
        quote.trackingAvailable = current.trackingAvailable;

        String legacyResponse = legacyClient.toJson(quote);
        String currentResponse = currentClient.toJson(quote);
        JsonParser parser = new JsonParser();
        assertEquals(parser.parse("""
                {"orderId":"O-318","shippingDays":4}
                """), parser.parse(legacyResponse));
        assertEquals(parser.parse("""
                {
                  "orderId":"O-318",
                  "deliveryWindow":{"minimumDays":2,"maximumDays":4},
                  "trackingAvailable":true
                }
                """), parser.parse(currentResponse));

        DeliveryQuote restoredLegacy = legacyClient.fromJson(legacyResponse, DeliveryQuote.class);
        DeliveryQuote restoredCurrent = currentClient.fromJson(currentResponse, DeliveryQuote.class);
        assertEquals(4, restoredLegacy.shippingDays);
        assertNull(restoredLegacy.deliveryWindow);
        assertEquals(2, restoredCurrent.deliveryWindow.minimumDays);
        assertEquals(4, restoredCurrent.deliveryWindow.maximumDays);
        assertEquals(Boolean.TRUE, restoredCurrent.trackingAvailable);
        assertEquals(0, restoredCurrent.shippingDays);
    }

    public static class DeliveryQuote {
        private String orderId;

        @Until(2.0)
        private int shippingDays;

        @Since(2.0)
        private DeliveryWindow deliveryWindow;

        @Since(2.0)
        private Boolean trackingAvailable;
    }

    public static class DeliveryWindow {
        private int minimumDays;
        private int maximumDays;
    }
}
