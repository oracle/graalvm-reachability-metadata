/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Property introspection customization: annotation mix-ins for types the application cannot edit,
 * global and per-class property naming strategies, `@JsonUnwrapped` prefixes, and
 * `@JsonAnyGetter`/`@JsonAnySetter` catch-all properties.
 *
 * A partner integration service exchanges flat snake_case customer records with a partner. The
 * address type comes from a vendor SDK, so its JSON shape is described through a mix-in registered
 * only on the partner-facing mapper. Some partner payloads use their own naming conventions, and
 * product listings carry vendor-specific attributes the service must keep without modelling them.
 */
public class PartnerFeedMixInNamingTest {

    private final ObjectMapper partnerMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(VendorAddress.class, VendorAddressMixIn.class)
            .build();

    private final ObjectMapper auditMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void mixInDescribesVendorTypeForThePartnerMapperOnly() throws Exception {
        VendorAddress address = new VendorAddress("1 Harbour Road", "Lisbon", "1100-001", "vdr-77");

        JsonNode partnerView = partnerMapper.valueToTree(address);
        assertEquals(List.of("street", "city", "zip"), fieldNames(partnerView));
        assertEquals("1100-001", partnerView.get("zip").asText());

        JsonNode auditView = auditMapper.valueToTree(address);
        assertEquals("vdr-77", auditView.get("internal_id").asText());
        assertEquals("1100-001", auditView.get("postal_code").asText());
        assertFalse(auditView.has("zip"));

        VendorAddress parsed = partnerMapper.readValue("""
                {"street":"9 Quay Street","city":"Porto","zip":"4050-001"}
                """, VendorAddress.class);
        assertEquals("9 Quay Street", parsed.getStreet());
        assertEquals("Porto", parsed.getCity());
        assertEquals("4050-001", parsed.getPostalCode());
        assertEquals("unassigned", parsed.getInternalId());
    }

    @Test
    void unwrappedAddressesAreFlattenedWithPrefixesUnderSnakeCase() throws Exception {
        CustomerAccount account = new CustomerAccount();
        account.accountId = "acc-1001";
        account.displayName = "Marta Silva";
        account.creditLimitCents = 250_000;
        account.billingAddress = new VendorAddress("1 Harbour Road", "Lisbon", "1100-001", "vdr-1");
        account.shippingAddress = new VendorAddress("7 Mill Lane", "Braga", "4700-002", "vdr-2");

        String json = partnerMapper.writeValueAsString(account);
        JsonNode record = partnerMapper.readTree(json);
        assertEquals(List.of(
                "account_id", "display_name", "credit_limit_cents",
                "billing_street", "billing_city", "billing_zip",
                "shipping_street", "shipping_city", "shipping_zip"), fieldNames(record));
        assertEquals("Braga", record.get("shipping_city").asText());
        assertEquals(250_000, record.get("credit_limit_cents").asLong());

        CustomerAccount restored = partnerMapper.readValue("""
                {
                  "account_id": "acc-2002",
                  "display_name": "Rui Costa",
                  "credit_limit_cents": 90000,
                  "billing_street": "3 Station Square",
                  "billing_city": "Coimbra",
                  "billing_zip": "3000-003",
                  "shipping_street": "5 Orchard Way",
                  "shipping_city": "Faro",
                  "shipping_zip": "8000-004"
                }
                """, CustomerAccount.class);
        assertEquals("acc-2002", restored.accountId);
        assertEquals("Rui Costa", restored.displayName);
        assertEquals(90_000, restored.creditLimitCents);
        assertEquals("Coimbra", restored.billingAddress.getCity());
        assertEquals("3000-003", restored.billingAddress.getPostalCode());
        assertEquals("5 Orchard Way", restored.shippingAddress.getStreet());
        assertEquals("8000-004", restored.shippingAddress.getPostalCode());
    }

    @Test
    void perClassNamingStrategiesOverrideTheMapperDefault() throws Exception {
        PartnerInvoice invoice = new PartnerInvoice();
        invoice.invoiceNumber = "INV-42";
        invoice.totalCents = 12_345;
        invoice.lineItems = List.of("widget", "gadget");

        JsonNode invoiceJson = partnerMapper.valueToTree(invoice);
        assertEquals(List.of("InvoiceNumber", "TotalCents", "LineItems"), fieldNames(invoiceJson));

        PartnerInvoice parsedInvoice = partnerMapper.readValue("""
                {"InvoiceNumber":"INV-43","TotalCents":500,"LineItems":["bolt"]}
                """, PartnerInvoice.class);
        assertEquals("INV-43", parsedInvoice.invoiceNumber);
        assertEquals(500, parsedInvoice.totalCents);
        assertEquals(List.of("bolt"), parsedInvoice.lineItems);

        LegacyStockLevel stock = new LegacyStockLevel();
        stock.warehouseCode = "LIS-2";
        stock.unitsOnHand = 17;
        JsonNode stockJson = partnerMapper.valueToTree(stock);
        assertEquals(List.of("X_WAREHOUSE_CODE", "X_UNITS_ON_HAND"), fieldNames(stockJson));

        LegacyStockLevel parsedStock = partnerMapper.readValue("""
                {"X_WAREHOUSE_CODE":"OPO-1","X_UNITS_ON_HAND":3}
                """, LegacyStockLevel.class);
        assertEquals("OPO-1", parsedStock.warehouseCode);
        assertEquals(3, parsedStock.unitsOnHand);
    }

    @Test
    void anySetterKeepsUnmodelledAttributesAndAnyGetterWritesThemBack() throws Exception {
        ProductListing listing = partnerMapper.readValue("""
                {
                  "sku": "SKU-9",
                  "unit_price_cents": 1999,
                  "colour": "teal",
                  "warranty_months": 24,
                  "eco_label": true
                }
                """, ProductListing.class);

        assertEquals("SKU-9", listing.sku);
        assertEquals(1999, listing.unitPriceCents);
        assertEquals(List.of("colour", "warranty_months", "eco_label"),
                new ArrayList<>(listing.getVendorAttributes().keySet()));
        assertEquals("teal", listing.getVendorAttributes().get("colour"));
        assertEquals(24, listing.getVendorAttributes().get("warranty_months"));
        assertEquals(Boolean.TRUE, listing.getVendorAttributes().get("eco_label"));

        listing.unitPriceCents = 1799;
        listing.putVendorAttribute("clearance", "yes");
        JsonNode written = partnerMapper.valueToTree(listing);
        assertEquals(List.of("sku", "unit_price_cents",
                "colour", "warranty_months", "eco_label", "clearance"), fieldNames(written));
        assertEquals(1799, written.get("unit_price_cents").asInt());
        assertEquals(24, written.get("warranty_months").asInt());
        assertTrue(written.get("eco_label").asBoolean());
        assertFalse(written.has("vendor_attributes"));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /// Stands in for a vendor SDK class: immutable, unannotated, and outside the application's
    /// control, so the partner mapper describes it through `VendorAddressMixIn`.
    public static final class VendorAddress {

        private final String street;
        private final String city;
        private final String postalCode;
        private final String internalId;

        public VendorAddress(String street, String city, String postalCode, String internalId) {
            this.street = street;
            this.city = city;
            this.postalCode = postalCode;
            this.internalId = internalId;
        }

        public VendorAddress(String street, String city, String postalCode) {
            this(street, city, postalCode, "unassigned");
        }

        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public String getInternalId() {
            return internalId;
        }
    }

    /// Partner-facing JSON shape of `VendorAddress`: explicit names, a creator, a hidden id.
    abstract static class VendorAddressMixIn {

        @JsonCreator
        VendorAddressMixIn(
                @JsonProperty("street") String street,
                @JsonProperty("city") String city,
                @JsonProperty("zip") String postalCode) {
        }

        @JsonProperty("street")
        abstract String getStreet();

        @JsonProperty("city")
        abstract String getCity();

        @JsonProperty("zip")
        abstract String getPostalCode();

        @JsonIgnore
        abstract String getInternalId();
    }

    public static class CustomerAccount {

        public String accountId;
        public String displayName;
        public long creditLimitCents;

        @JsonUnwrapped(prefix = "billing_")
        public VendorAddress billingAddress;

        @JsonUnwrapped(prefix = "shipping_")
        public VendorAddress shippingAddress;
    }

    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class PartnerInvoice {

        public String invoiceNumber;
        public long totalCents;
        public List<String> lineItems;
    }

    @JsonNaming(LegacyPrefixedUpperSnakeStrategy.class)
    public static class LegacyStockLevel {

        public String warehouseCode;
        public int unitsOnHand;
    }

    /// The legacy warehouse system prefixes every column with `X_` and upper-cases it.
    public static class LegacyPrefixedUpperSnakeStrategy extends PropertyNamingStrategies.NamingBase {

        private final PropertyNamingStrategies.SnakeCaseStrategy snakeCase =
                new PropertyNamingStrategies.SnakeCaseStrategy();

        @Override
        public String translate(String propertyName) {
            return "X_" + snakeCase.translate(propertyName).toUpperCase(Locale.ROOT);
        }
    }

    public static class ProductListing {

        public String sku;
        public int unitPriceCents;

        private final Map<String, Object> vendorAttributes = new LinkedHashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getVendorAttributes() {
            return vendorAttributes;
        }

        @JsonAnySetter
        public void putVendorAttribute(String name, Object value) {
            vendorAttributes.put(name, value);
        }
    }
}
