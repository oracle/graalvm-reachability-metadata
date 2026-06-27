/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_swagger_2;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jsonschema_module_swagger_2Test {

    private final SchemaGenerator generator = createGenerator();

    @Test
    void generatesSchemaAttributesFromSwaggerAnnotationsOnFieldsTypesAndMethods() {
        JsonNode schema = this.generator.generateSchema(Product.class);
        JsonNode properties = schema.required("properties");

        assertThat(schema.required("title").asText()).isEqualTo("Product schema");
        assertThat(schema.required("description").asText()).isEqualTo("A product exposed through the catalog API");
        assertThat(schema.required("additionalProperties").asBoolean()).isFalse();
        assertThat(properties.has("internalNote")).isFalse();

        JsonNode sku = properties.required("sku");
        assertThat(sku.required("title").asText()).isEqualTo("Stock keeping unit");
        assertThat(sku.required("description").asText()).isEqualTo("Public product identifier");
        assertThat(sku.required("format").asText()).isEqualTo("catalog-code");
        assertThat(sku.required("pattern").asText()).isEqualTo("^[A-Z0-9-]+$");
        assertThat(sku.required("minLength").asInt()).isEqualTo(3);
        assertThat(sku.required("maxLength").asInt()).isEqualTo(12);
        assertThat(sku.required("default").asText()).isEqualTo("ABC-1");
        assertThat(arrayTexts(sku.required("enum"))).containsExactly("ABC-1", "XYZ-9");

        JsonNode requiredProperties = schema.required("required");
        assertThat(arrayTexts(requiredProperties)).contains("sku", "price", "tags");

        JsonNode price = properties.required("price");
        assertThat(price.required("exclusiveMaximum").decimalValue()).isEqualByComparingTo(new BigDecimal("999.99"));
        assertThat(price.required("minimum").decimalValue()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(price.required("multipleOf").decimalValue()).isEqualByComparingTo(new BigDecimal("0.01"));

        JsonNode discount = properties.required("discountPercentage");
        assertThat(discount.required("exclusiveMinimum").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(discount.required("maximum").decimalValue()).isEqualByComparingTo(new BigDecimal("100"));

        JsonNode tags = properties.required("tags");
        assertThat(tags.required("minItems").asInt()).isEqualTo(1);
        assertThat(tags.required("maxItems").asInt()).isEqualTo(3);
        assertThat(tags.required("uniqueItems").asBoolean()).isTrue();
        assertTypeIncludes(tags.required("items"), "string", "null");
        assertThat(tags.required("items").required("minLength").asInt()).isEqualTo(2);

        JsonNode readOnlySupplier = properties.required("supplier");
        assertThat(readOnlySupplier.required("readOnly").asBoolean()).isTrue();

        JsonNode writeOnlyPriceDetails = properties.required("priceDetails");
        assertThat(writeOnlyPriceDetails.required("writeOnly").asBoolean()).isTrue();
    }

    @Test
    void supportsReferencesCompositionAndDefinitionNames() {
        JsonNode schema = this.generator.generateSchema(Product.class);
        JsonNode properties = schema.required("properties");
        JsonNode definitions = schema.required("$defs");

        assertThat(definitions.has("PriceDetailsSchema")).isTrue();
        JsonNode priceDetailsDefinition = definitions.required("PriceDetailsSchema");
        assertThat(priceDetailsDefinition.required("title").asText()).isEqualTo("Price details");
        assertThat(priceDetailsDefinition.required("description").asText()).isEqualTo("Expanded price representation");

        JsonNode priceDetails = properties.required("priceDetails");
        assertThat(priceDetails.required("$ref").asText()).isEqualTo("#/$defs/PriceDetailsSchema");
        assertThat(priceDetails.required("writeOnly").asBoolean()).isTrue();

        JsonNode supplier = properties.required("supplier");
        assertThat(supplier.required("$ref").asText()).isEqualTo("https://example.test/schemas/Supplier.json");
        assertThat(supplier.required("readOnly").asBoolean()).isTrue();

        JsonNode extension = properties.required("extension");
        assertThat(extension.required("minProperties").asInt()).isEqualTo(1);
        assertThat(extension.required("maxProperties").asInt()).isEqualTo(4);
        assertThat(arrayTexts(extension.required("required"))).contains("kind");
        assertThat(extension.has("not")).isTrue();
        assertThat(containsText(extension, "Audited") || containsText(extension, "auditId")).isTrue();
        assertThat(containsText(extension, "Promotion") || containsText(extension, "promotionCode")).isTrue();
        assertThat(containsText(extension, "Clearance") || containsText(extension, "clearanceReason")).isTrue();
        assertThat(containsText(extension, "Digital") || containsText(extension, "downloadUrl")).isTrue();
        assertThat(containsText(extension, "Physical") || containsText(extension, "shippingWeight")).isTrue();
    }

    @Test
    void resolvesDeclaredSubtypesAndResetsExternalReferenceProviderBetweenGenerations() {
        JsonNode referencedRootSchema = this.generator.generateSchema(ExternallyDefinedComponent.class);
        assertThat(referencedRootSchema.has("$ref")).isFalse();
        assertThat(referencedRootSchema.required("properties").has("code")).isTrue();

        JsonNode holderSchema = this.generator.generateSchema(ComponentHolder.class);
        assertThat(holderSchema.required("properties").has("externalComponent")).isTrue();
        assertThat(containsText(holderSchema, "https://example.test/schemas/ExternalComponent.json")).isTrue();

        JsonNode shippingSchema = this.generator.generateSchema(ShippingSelection.class);
        assertThat(shippingSchema.required("properties").has("carrier")).isTrue();
        boolean hasPostalCarrier = containsText(shippingSchema, "PostalCarrier")
                || containsText(shippingSchema, "postalCode");
        boolean hasCourierCarrier = containsText(shippingSchema, "CourierCarrier")
                || containsText(shippingSchema, "courierName");
        assertThat(hasPostalCarrier).isTrue();
        assertThat(hasCourierCarrier).isTrue();

        assertThat(shippingSchema.required("properties").has("payment")).isTrue();
        boolean hasCardPayment = containsText(shippingSchema, "CardPayment")
                || containsText(shippingSchema, "lastFourDigits");
        boolean hasBankTransferPayment = containsText(shippingSchema, "BankTransferPayment")
                || containsText(shippingSchema, "iban");
        assertThat(hasCardPayment).isTrue();
        assertThat(hasBankTransferPayment).isTrue();
    }

    private static SchemaGenerator createGenerator() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2019_09, OptionPreset.PLAIN_JSON)
                .with(Option.DEFINITIONS_FOR_ALL_OBJECTS,
                        Option.NULLABLE_ARRAY_ITEMS_ALLOWED,
                        Option.NULLABLE_FIELDS_BY_DEFAULT)
                .with(new Swagger2Module());
        return new SchemaGenerator(configBuilder.build());
    }

    private static void assertTypeIncludes(JsonNode schema, String... expectedTypes) {
        assertThat(schema.has("type")).isTrue();
        JsonNode type = schema.required("type");
        if (type.isArray()) {
            assertThat(arrayTexts(type)).contains(expectedTypes);
        } else {
            assertThat(type.asText()).isIn((Object[]) expectedTypes);
        }
    }

    private static List<String> arrayTexts(JsonNode arrayNode) {
        assertThat(arrayNode.isArray()).isTrue();
        List<String> values = new ArrayList<>();
        arrayNode.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static boolean containsText(JsonNode node, String value) {
        if (node.isValueNode()) {
            return node.asText().contains(value);
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsText(item, value)) {
                    return true;
                }
            }
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().contains(value) || containsText(field.getValue(), value)) {
                return true;
            }
        }
        return false;
    }

    @Schema(title = "Product schema", description = "A product exposed through the catalog API",
            additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public static class Product {
        @Schema(hidden = true)
        public String internalNote;

        @Schema(name = "sku", title = "Stock keeping unit", description = "Public product identifier",
                requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ABC-1", "XYZ-9"},
                defaultValue = "ABC-1", minLength = 3, maxLength = 12, format = "catalog-code",
                pattern = "^[A-Z0-9-]+$")
        public String stockKeepingUnit;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0.01", maximum = "999.99",
                exclusiveMaximum = true, multipleOf = 0.01)
        public BigDecimal price;

        @Schema(name = "discountPercentage", minimum = "0", maximum = "100", exclusiveMinimum = true)
        public int discount;

        @Schema(implementation = PriceDetails.class, accessMode = Schema.AccessMode.WRITE_ONLY)
        public Object priceDetails;

        @Schema(ref = "https://example.test/schemas/Supplier.json", accessMode = Schema.AccessMode.READ_ONLY)
        public Supplier supplier;

        @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(nullable = true, minLength = 2), minItems = 1, maxItems = 3, uniqueItems = true)
        public List<String> tags;

        @Schema(name = "extension", not = ForbiddenPayload.class, allOf = {Audited.class},
                anyOf = {Promotion.class, Clearance.class}, oneOf = {Digital.class, Physical.class},
                minProperties = 1, maxProperties = 4, requiredProperties = {"kind"})
        public Object extensionPayload;

    }

    @Schema(name = "PriceDetailsSchema", title = "Price details", description = "Expanded price representation")
    public static class PriceDetails {
        public BigDecimal amount;
        public String currency;
    }

    public static class Supplier {
        public String name;
    }

    public static class ForbiddenPayload {
        public String reason;
    }

    public static class Audited {
        public String auditId;
    }

    public static class Promotion {
        public String promotionCode;
    }

    public static class Clearance {
        public String clearanceReason;
    }

    public static class Digital {
        public String downloadUrl;
    }

    public static class Physical {
        public BigDecimal shippingWeight;
    }

    @Schema(ref = "https://example.test/schemas/ExternalComponent.json")
    public static class ExternallyDefinedComponent {
        public String code;
    }

    public static class ComponentHolder {
        public ExternallyDefinedComponent externalComponent;
    }

    public static class ShippingSelection {
        public Carrier carrier;
        public Payment payment;
    }

    @Schema(subTypes = {PostalCarrier.class, CourierCarrier.class})
    public interface Carrier {
    }

    public static class PostalCarrier implements Carrier {
        public String postalCode;
    }

    public static class CourierCarrier implements Carrier {
        public String courierName;
    }

    @Schema(anyOf = {CardPayment.class, BankTransferPayment.class})
    public interface Payment {
    }

    public static class CardPayment implements Payment {
        public String lastFourDigits;
    }

    public static class BankTransferPayment implements Payment {
        public String iban;
    }
}
