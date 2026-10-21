/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * JSON property naming: the `FieldNamingPolicy` variants, a custom `FieldNamingStrategy` with
 * legacy alternate names, `@SerializedName` overrides and alternates, and name conflicts.
 *
 * A CRM synchronizes one contact model with several partner systems, each of which spells its
 * JSON properties differently: an ERP wants `UPPER_SNAKE_CASE`, a marketing tool wants
 * `kebab-case`, a spreadsheet export wants "Title Case With Spaces", and so on. The contact's
 * CRM identifier is pinned to `id` everywhere, the phone number accepts the names older mobile
 * clients still send, and a warehouse integration maps fields through its own naming strategy
 * that also accepts the camelCase names of its previous release. Map keys and enum constants
 * keep their own spelling regardless of the policy, and a model that maps two fields to the same
 * property name is rejected before any payload is written.
 */
public class CrmContactSyncPropertyNamingTest {

    @Test
    void everyNamingPolicySpellsTheSameContactDifferentlyAndRoundTrips() {
        Map<FieldNamingPolicy, Set<String>> expectedKeys = new LinkedHashMap<>();
        expectedKeys.put(FieldNamingPolicy.IDENTITY,
                Set.of("id", "firstName", "lastName", "emailAddress", "tier", "phone"));
        expectedKeys.put(FieldNamingPolicy.UPPER_CAMEL_CASE,
                Set.of("id", "FirstName", "LastName", "EmailAddress", "Tier", "phone"));
        expectedKeys.put(FieldNamingPolicy.UPPER_CAMEL_CASE_WITH_SPACES,
                Set.of("id", "First Name", "Last Name", "Email Address", "Tier", "phone"));
        expectedKeys.put(FieldNamingPolicy.UPPER_CASE_WITH_UNDERSCORES,
                Set.of("id", "FIRST_NAME", "LAST_NAME", "EMAIL_ADDRESS", "TIER", "phone"));
        expectedKeys.put(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES,
                Set.of("id", "first_name", "last_name", "email_address", "tier", "phone"));
        expectedKeys.put(FieldNamingPolicy.LOWER_CASE_WITH_DASHES,
                Set.of("id", "first-name", "last-name", "email-address", "tier", "phone"));
        expectedKeys.put(FieldNamingPolicy.LOWER_CASE_WITH_DOTS,
                Set.of("id", "first.name", "last.name", "email.address", "tier", "phone"));

        for (Map.Entry<FieldNamingPolicy, Set<String>> entry : expectedKeys.entrySet()) {
            Gson partner = new GsonBuilder().setFieldNamingPolicy(entry.getKey()).create();
            Contact contact = Contact.sample();

            String payload = partner.toJson(contact);
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            assertEquals(new TreeSet<>(entry.getValue()), new TreeSet<>(json.keySet()),
                    "keys for " + entry.getKey());
            Contact restored = partner.fromJson(payload, Contact.class);
            assertEquals("CRM-1001", restored.crmId, "@SerializedName id under " + entry.getKey());
            assertEquals("Ada", restored.firstName);
            assertEquals("Lovelace", restored.lastName);
            assertEquals("ada@example.org", restored.emailAddress);
            assertEquals(Tier.GOLD_MEMBER, restored.tier);
            assertEquals("+44 20 7946 0000", restored.phone);
        }
    }

    @Test
    void namingPolicyRenamesFieldsButNotEnumConstantsOrMapKeys() {
        Gson marketing = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
                .create();
        Segment segment = new Segment();
        segment.segmentName = "Loyal Customers";
        segment.minimumTier = Tier.GOLD_MEMBER;
        segment.customAttributes = new LinkedHashMap<>();
        segment.customAttributes.put("preferredChannel", "email");
        segment.customAttributes.put("lastCampaignId", "SPRING-24");

        JsonObject json = JsonParser.parseString(marketing.toJson(segment)).getAsJsonObject();

        assertEquals("Loyal Customers", json.get("segment-name").getAsString());
        assertEquals("GOLD_MEMBER", json.get("minimum-tier").getAsString(),
                "enum constants keep their declared name");
        JsonObject attributes = json.getAsJsonObject("custom-attributes");
        assertEquals(Set.of("preferredChannel", "lastCampaignId"), attributes.keySet(),
                "map keys are data, not field names");

        Segment restored = marketing.fromJson(json, Segment.class);
        assertEquals("Loyal Customers", restored.segmentName);
        assertEquals(Tier.GOLD_MEMBER, restored.minimumTier);
        assertEquals("SPRING-24", restored.customAttributes.get("lastCampaignId"));
    }

    @Test
    void phoneAcceptsLegacyAlternateNamesButIsAlwaysWrittenUnderItsPrimaryName() {
        Gson api = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        Contact fromOldIos = api.fromJson("""
                {"id": "CRM-2001", "first_name": "Grace", "phoneNumber": "+1 202 555 0101"}
                """, Contact.class);
        Contact fromOldAndroid = api.fromJson("""
                {"id": "CRM-2002", "first_name": "Linus", "tel": "+358 9 1234 567"}
                """, Contact.class);
        Contact fromBoth = api.fromJson("""
                {"id": "CRM-2003", "tel": "+1 000", "phone": "+1 111", "phoneNumber": "+1 222"}
                """, Contact.class);

        assertEquals("+1 202 555 0101", fromOldIos.phone);
        assertEquals("Grace", fromOldIos.firstName);
        assertEquals("+358 9 1234 567", fromOldAndroid.phone);
        assertEquals("+1 222", fromBoth.phone, "the last occurrence in the document wins");

        JsonObject written = JsonParser.parseString(api.toJson(fromOldIos)).getAsJsonObject();
        assertEquals("+1 202 555 0101", written.get("phone").getAsString());
        assertTrue(!written.has("phoneNumber") && !written.has("tel"),
                "alternates are only read, never written");
    }

    @Test
    void recordComponentsFollowThePolicyAndTheirOwnSerializedNames() {
        Gson erp = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CASE_WITH_UNDERSCORES)
                .create();
        Address address = new Address("12 St James's Square", "SW1Y 4LB", "GB");

        JsonObject json = JsonParser.parseString(erp.toJson(address)).getAsJsonObject();

        assertEquals(Set.of("STREET_LINE", "POSTAL_CODE", "iso_country"),
                new TreeSet<>(json.keySet()));
        assertEquals(address, erp.fromJson(json, Address.class));
        assertEquals(address, erp.fromJson("""
                {"STREET_LINE": "12 St James's Square", "POSTAL_CODE": "SW1Y 4LB",
                 "country": "GB"}
                """, Address.class), "record component alternate name");
    }

    @Test
    void warehouseStrategyPrefixesNamesHonoursLabelsAndAcceptsPreviousRelease() {
        Gson warehouse = new GsonBuilder()
                .setFieldNamingStrategy(new WarehouseNamingStrategy())
                .create();
        StockItem item = new StockItem();
        item.stockKeepingUnit = "SKU-778";
        item.quantityOnHand = 41;
        item.binLocation = "A-07-3";

        JsonObject json = JsonParser.parseString(warehouse.toJson(item)).getAsJsonObject();

        assertEquals(Set.of("wh_stock_keeping_unit", "wh_quantity_on_hand", "wh_bin"),
                new TreeSet<>(json.keySet()));
        assertEquals(41, json.get("wh_quantity_on_hand").getAsInt());
        assertEquals("A-07-3", json.get("wh_bin").getAsString(), "@WarehouseLabel wins");

        StockItem current = warehouse.fromJson(json, StockItem.class);
        assertEquals("SKU-778", current.stockKeepingUnit);
        assertEquals(41, current.quantityOnHand);
        assertEquals("A-07-3", current.binLocation);

        StockItem previousRelease = warehouse.fromJson("""
                {"stockKeepingUnit": "SKU-12", "quantityOnHand": 3, "binLocation": "C-01-1"}
                """, StockItem.class);
        assertEquals("SKU-12", previousRelease.stockKeepingUnit);
        assertEquals(3, previousRelease.quantityOnHand);
        assertEquals("C-01-1", previousRelease.binLocation);

        StockItem unknown = warehouse.fromJson("""
                {"stock_keeping_unit": "SKU-99", "quantity": 8}
                """, StockItem.class);
        assertNull(unknown.stockKeepingUnit, "names outside the strategy are ignored");
        assertEquals(0, unknown.quantityOnHand);
    }

    @Test
    void modelThatMapsTwoFieldsToTheSamePropertyIsRejected() {
        Gson crm = new Gson();
        Gson snakeCase = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        IllegalArgumentException annotated = assertThrows(IllegalArgumentException.class,
                () -> crm.toJson(new DuplicateEmailContact()));
        IllegalArgumentException translated = assertThrows(IllegalArgumentException.class,
                () -> snakeCase.fromJson("{}", CollidingImportRow.class));

        assertTrue(annotated.getMessage().contains("declares multiple JSON fields named 'email'"),
                annotated.getMessage());
        assertTrue(translated.getMessage().contains("declares multiple JSON fields named"),
                translated.getMessage());
        assertTrue(translated.getMessage().contains("'postal_code'"), translated.getMessage());
        assertEquals("{\"postalCode\":\"N1\",\"postal_code\":\"N2\"}",
                crm.toJson(new CollidingImportRow()), "IDENTITY keeps the two names apart");
    }

    enum Tier {
        STANDARD,
        GOLD_MEMBER
    }

    static class Contact {
        @SerializedName("id")
        String crmId;
        String firstName;
        String lastName;
        String emailAddress;
        Tier tier;
        @SerializedName(value = "phone", alternate = {"phoneNumber", "tel"})
        String phone;

        static Contact sample() {
            Contact contact = new Contact();
            contact.crmId = "CRM-1001";
            contact.firstName = "Ada";
            contact.lastName = "Lovelace";
            contact.emailAddress = "ada@example.org";
            contact.tier = Tier.GOLD_MEMBER;
            contact.phone = "+44 20 7946 0000";
            return contact;
        }
    }

    static class Segment {
        String segmentName;
        Tier minimumTier;
        Map<String, String> customAttributes;
    }

    record Address(
            String streetLine,
            String postalCode,
            @SerializedName(value = "iso_country", alternate = "country") String countryCode) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface WarehouseLabel {
        String value();
    }

    static class StockItem {
        String stockKeepingUnit;
        int quantityOnHand;
        @WarehouseLabel("bin")
        String binLocation;
    }

    /// Prefixes every property with `wh_`, takes the snake_case form or an explicit
    /// `@WarehouseLabel`, and still reads the plain Java names the previous release sent.
    static final class WarehouseNamingStrategy implements FieldNamingStrategy {
        @Override
        public String translateName(Field field) {
            // Checkstyle: allow direct annotation access
            Field fieldAnnotationAccess = field;
            WarehouseLabel label = fieldAnnotationAccess.getAnnotation(WarehouseLabel.class);
            // Checkstyle: disallow direct annotation access
            String base = label != null
                    ? label.value()
                    : FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.translateName(field);
            return "wh_" + base;
        }

        @Override
        public List<String> alternateNames(Field field) {
            return List.of(field.getName());
        }
    }

    static class DuplicateEmailContact {
        String email = "primary@example.org";
        @SerializedName("email")
        String emailAddress = "secondary@example.org";
    }

    static class CollidingImportRow {
        String postalCode = "N1";
        @SerializedName("postal_code")
        String zip = "N2";
    }
}
