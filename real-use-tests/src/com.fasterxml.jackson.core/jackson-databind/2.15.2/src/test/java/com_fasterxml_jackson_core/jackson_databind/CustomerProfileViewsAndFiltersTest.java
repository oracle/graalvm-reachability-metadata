/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Selective property exposure: `@JsonView` serialization and deserialization views with view
 * inheritance and `DEFAULT_VIEW_INCLUSION`, and `@JsonFilter` dynamic property filtering through
 * `SimpleFilterProvider`, `SimpleBeanPropertyFilter` and a custom masking filter.
 *
 * A customer service returns one profile model to three audiences: the public storefront, support
 * agents and the internal billing system. Views decide which fields each audience sees and which
 * fields a self-service update may change; filters let the audit exporter drop or mask sensitive
 * fields chosen at request time.
 */
public class CustomerProfileViewsAndFiltersTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .build();

    @Test
    void eachAudienceSeesOnlyTheFieldsOfItsView() throws Exception {
        CustomerProfile profile = sampleProfile();

        JsonNode storefront = mapper.readTree(
                mapper.writerWithView(Views.Storefront.class).writeValueAsString(profile));
        assertEquals(2, storefront.size());
        assertEquals("cust-42", storefront.get("id").asText());
        assertEquals("Ada L.", storefront.get("displayName").asText());

        JsonNode support = mapper.readTree(
                mapper.writerWithView(Views.Support.class).writeValueAsString(profile));
        assertEquals(4, support.size());
        assertEquals("ada@example.org", support.get("email").asText());
        assertEquals(2, support.get("openTickets").asInt());
        assertFalse(support.has("creditLimit"));

        JsonNode billing = mapper.readTree(
                mapper.writerWithView(Views.Billing.class).writeValueAsString(profile));
        assertEquals(5, billing.size());
        assertEquals(1500, billing.get("creditLimit").asInt());
        assertFalse(billing.has("internalNotes"));
    }

    @Test
    void unannotatedFieldsFollowDefaultViewInclusion() throws Exception {
        CustomerProfile profile = sampleProfile();

        ObjectMapper lenient = JsonMapper.builder()
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        JsonNode lenientView = lenient.readTree(
                lenient.writerWithView(Views.Storefront.class).writeValueAsString(profile));
        assertEquals("prefers phone", lenientView.get("internalNotes").asText());

        JsonNode strictView = mapper.readTree(
                mapper.writerWithView(Views.Storefront.class).writeValueAsString(profile));
        assertFalse(strictView.has("internalNotes"));

        JsonNode noView = mapper.readTree(mapper.writeValueAsString(profile));
        assertEquals(6, noView.size());
        assertEquals("prefers phone", noView.get("internalNotes").asText());
    }

    @Test
    void selfServiceUpdateIgnoresFieldsOutsideItsView() throws Exception {
        String request = """
                {
                  "id": "cust-42",
                  "displayName": "Ada Lovelace",
                  "email": "ada@lovelace.dev",
                  "openTickets": 0,
                  "creditLimit": 999999,
                  "internalNotes": "vip"
                }
                """;

        CustomerProfile update = mapper.readerWithView(Views.Support.class)
                .forType(CustomerProfile.class)
                .readValue(request);

        assertEquals("cust-42", update.id);
        assertEquals("Ada Lovelace", update.displayName);
        assertEquals("ada@lovelace.dev", update.email);
        assertEquals(0, update.openTickets);
        assertEquals(0, update.creditLimit);
        assertNull(update.internalNotes);

        CustomerProfile billingImport = mapper.readerWithView(Views.Billing.class)
                .forType(CustomerProfile.class)
                .readValue(request);
        assertEquals(999999, billingImport.creditLimit);
        assertNull(billingImport.internalNotes);
    }

    @Test
    void auditExporterDropsFieldsChosenPerRequest() throws Exception {
        AuditExport export = sampleExport();

        FilterProvider withoutSecrets = new SimpleFilterProvider()
                .addFilter("auditEntry", SimpleBeanPropertyFilter.serializeAllExcept("ipAddress", "sessionToken"));
        JsonNode redacted = mapper.readTree(mapper.writer(withoutSecrets).writeValueAsString(export));
        JsonNode first = redacted.get("entries").get(0);
        assertEquals("login", first.get("action").asText());
        assertEquals("cust-42", first.get("customerId").asText());
        assertFalse(first.has("ipAddress"));
        assertFalse(first.has("sessionToken"));
        assertEquals("2026-10-01", redacted.get("day").asText());

        FilterProvider actionsOnly = new SimpleFilterProvider()
                .addFilter("auditEntry", SimpleBeanPropertyFilter.filterOutAllExcept("action"));
        JsonNode summary = mapper.readTree(mapper.writer(actionsOnly).writeValueAsString(export));
        assertEquals(1, summary.get("entries").get(1).size());
        assertEquals("password-change", summary.get("entries").get(1).get("action").asText());
    }

    @Test
    void customFilterMasksSensitiveValuesInsteadOfDroppingThem() throws Exception {
        FilterProvider masking = new SimpleFilterProvider()
                .addFilter("auditEntry", new MaskingFilter("ipAddress", "sessionToken"));

        JsonNode masked = mapper.readTree(mapper.writer(masking).writeValueAsString(sampleExport()));

        JsonNode second = masked.get("entries").get(1);
        assertEquals("***", second.get("ipAddress").asText());
        assertEquals("***", second.get("sessionToken").asText());
        assertEquals("password-change", second.get("action").asText());
        assertEquals("cust-42", second.get("customerId").asText());
    }

    @Test
    void filteredTypeRequiresARegisteredFilterUnlessConfiguredOtherwise() throws Exception {
        AuditExport export = sampleExport();

        InvalidDefinitionException missing = assertThrows(InvalidDefinitionException.class,
                () -> mapper.writeValueAsString(export));
        assertTrue(missing.getMessage().contains("auditEntry"), missing.getMessage());

        FilterProvider permissive = new SimpleFilterProvider().setFailOnUnknownId(false);
        JsonNode everything = mapper.readTree(mapper.writer(permissive).writeValueAsString(export));
        assertEquals("10.0.0.7", everything.get("entries").get(0).get("ipAddress").asText());

        FilterProvider withDefault = new SimpleFilterProvider()
                .setDefaultFilter(SimpleBeanPropertyFilter.serializeAllExcept("sessionToken"));
        JsonNode defaulted = mapper.readTree(mapper.writer(withDefault).writeValueAsString(export));
        assertTrue(defaulted.get("entries").get(0).has("ipAddress"));
        assertFalse(defaulted.get("entries").get(0).has("sessionToken"));
    }

    @Test
    void viewsAndFiltersCombineOnOneWriter() throws Exception {
        FilterProvider withoutSecrets = new SimpleFilterProvider()
                .addFilter("auditEntry", SimpleBeanPropertyFilter.serializeAllExcept("sessionToken"));

        JsonNode tree = mapper.readTree(mapper.writer(withoutSecrets)
                .withView(Views.Support.class)
                .writeValueAsString(sampleExport()));

        JsonNode entry = tree.get("entries").get(0);
        assertEquals("login", entry.get("action").asText());
        assertEquals("cust-42", entry.get("customerId").asText());
        assertFalse(entry.has("ipAddress"));
        assertFalse(entry.has("sessionToken"));
        assertFalse(tree.has("day"));
    }

    private static CustomerProfile sampleProfile() {
        CustomerProfile profile = new CustomerProfile();
        profile.id = "cust-42";
        profile.displayName = "Ada L.";
        profile.email = "ada@example.org";
        profile.openTickets = 2;
        profile.creditLimit = 1500;
        profile.internalNotes = "prefers phone";
        return profile;
    }

    private static AuditExport sampleExport() {
        AuditExport export = new AuditExport();
        export.day = "2026-10-01";
        export.entries.add(new AuditEntry("login", "cust-42", "10.0.0.7", "tok-a1"));
        export.entries.add(new AuditEntry("password-change", "cust-42", "10.0.0.9", "tok-b2"));
        return export;
    }

    /** Audiences, each seeing everything its parent sees. */
    static final class Views {

        interface Storefront {
        }

        interface Support extends Storefront {
        }

        interface Billing extends Support {
        }
    }

    @JsonPropertyOrder({"id", "displayName", "email", "openTickets", "creditLimit", "internalNotes"})
    static class CustomerProfile {

        @JsonView(Views.Storefront.class)
        public String id;

        @JsonView(Views.Storefront.class)
        public String displayName;

        @JsonView(Views.Support.class)
        public String email;

        @JsonView(Views.Support.class)
        public int openTickets;

        @JsonView(Views.Billing.class)
        public int creditLimit;

        public String internalNotes;
    }

    static class AuditExport {

        public String day;

        @JsonView(Views.Support.class)
        public List<AuditEntry> entries = new ArrayList<>();
    }

    @JsonFilter("auditEntry")
    @JsonPropertyOrder({"action", "customerId", "ipAddress", "sessionToken"})
    static class AuditEntry {

        @JsonView(Views.Support.class)
        public String action;

        @JsonView(Views.Support.class)
        public String customerId;

        @JsonView(Views.Billing.class)
        public String ipAddress;

        @JsonView(Views.Support.class)
        public String sessionToken;

        AuditEntry(String action, String customerId, String ipAddress, String sessionToken) {
            this.action = action;
            this.customerId = customerId;
            this.ipAddress = ipAddress;
            this.sessionToken = sessionToken;
        }
    }

    /** Writes the named properties as a fixed mask; writes every other property unchanged. */
    static final class MaskingFilter extends SimpleBeanPropertyFilter {

        private final List<String> masked;

        MaskingFilter(String... masked) {
            this.masked = List.of(masked);
        }

        @Override
        public void serializeAsField(Object pojo, JsonGenerator generator, SerializerProvider provider,
                PropertyWriter writer) throws Exception {
            if (masked.contains(writer.getName())) {
                generator.writeStringField(writer.getName(), "***");
            } else {
                writer.serializeAsField(pojo, generator, provider);
            }
        }
    }
}
