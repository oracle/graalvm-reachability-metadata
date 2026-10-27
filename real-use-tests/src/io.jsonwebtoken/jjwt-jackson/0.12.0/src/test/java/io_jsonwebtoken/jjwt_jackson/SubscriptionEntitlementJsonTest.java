/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's custom ObjectMapper module integration for subscription entitlement JSON.
public class SubscriptionEntitlementJsonTest {
    @Test
    @Timeout(60)
    public void exportsSubscriptionDatesAndOptionalBillingFields() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>(mapper);
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>(mapper);
        LocalDate startsOn = LocalDate.of(2030, 2, 1);
        LocalDate renewsOn = startsOn.plusMonths(1);
        Claims entitlement = Jwts.claims()
                .issuer("subscription-service")
                .subject("account-704")
                .add("subscription", Map.of(
                        "plan", "team",
                        "starts_on", startsOn,
                        "renews_on", renewsOn,
                        "billing_reference", Optional.of("purchase-order-82"),
                        "coupon", Optional.empty()))
                .build();

        byte[] exported = serializer.serialize(entitlement);
        String json = new String(exported, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"starts_on\":\"2030-02-01\""));
        assertTrue(json.contains("\"renews_on\":\"2030-03-01\""));
        assertTrue(json.contains("\"billing_reference\":\"purchase-order-82\""));
        assertTrue(json.contains("\"coupon\":null"));

        Claims imported = Jwts.claims().add(deserializer.deserialize(exported)).build();
        Map<?, ?> subscription = imported.get("subscription", Map.class);
        LocalDate importedStart = LocalDate.parse((String) subscription.get("starts_on"));
        LocalDate importedRenewal = LocalDate.parse((String) subscription.get("renews_on"));

        assertEquals("subscription-service", imported.getIssuer());
        assertEquals("account-704", imported.getSubject());
        assertEquals("team", subscription.get("plan"));
        assertEquals(startsOn, importedStart);
        assertEquals(renewsOn, importedRenewal);
        assertEquals("purchase-order-82", subscription.get("billing_reference"));
        assertTrue(subscription.containsKey("coupon"));
        assertNull(subscription.get("coupon"));
    }
}
