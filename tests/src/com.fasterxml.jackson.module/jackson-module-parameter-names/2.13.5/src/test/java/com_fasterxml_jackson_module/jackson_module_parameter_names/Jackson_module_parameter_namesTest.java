/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_parameter_names;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Jackson_module_parameter_namesTest {
    @Test
    void deserializesRecordCanonicalConstructorFromImplicitParameterNames() throws Exception {
        JsonMapper mapper = mapperWithDefaultModule();
        String json = """
                {
                  "orderId": "order-7",
                  "amount": 19.95,
                  "shippingAddress": {
                    "street": "1 Library Way",
                    "city": "Prague",
                    "postalCode": "11000"
                  },
                  "lineItems": [
                    {"sku": "book", "quantity": 2},
                    {"sku": "pen", "quantity": 5}
                  ],
                  "attributes": {"priority": "standard", "gift": "false"},
                  "status": "CONFIRMED"
                }
                """;

        Order order = mapper.readValue(json, Order.class);

        assertThat(order.orderId()).isEqualTo("order-7");
        assertThat(order.amount()).isEqualByComparingTo("19.95");
        assertThat(order.shippingAddress()).isEqualTo(new Address("1 Library Way", "Prague", "11000"));
        assertThat(order.lineItems()).containsExactly(new LineItem("book", 2), new LineItem("pen", 5));
        assertThat(order.attributes()).containsExactlyInAnyOrderEntriesOf(Map.of("priority", "standard", "gift", "false"));
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void deserializesAnnotatedMultiArgumentCreatorFromImplicitParameterNames() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .build();

        CustomerName name = mapper.readValue(
                "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}",
                CustomerName.class);

        assertThat(name).isEqualTo(new CustomerName("Ada", "Lovelace"));
    }

    @Test
    void supportsCaseInsensitiveMatchingForImplicitCreatorParameters() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build();

        InventoryItem item = mapper.readValue(
                "{\"SKU\":\"A-100\",\"AVAILABLECOUNT\":12}",
                InventoryItem.class);

        assertThat(item).isEqualTo(new InventoryItem("A-100", 12));
    }

    @Test
    void propertiesBindingModeTreatsSingleArgumentDefaultCreatorAsObjectCreator() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .build();

        ProductCode code = mapper.readValue("{\"value\":\"SKU-42\"}", ProductCode.class);

        assertThat(code).isEqualTo(new ProductCode("SKU-42"));
    }

    @Test
    void delegatingBindingModeTreatsSingleArgumentDefaultCreatorAsScalarCreator() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.DELEGATING))
                .build();

        ScalarToken token = mapper.readValue("\"token-123\"", ScalarToken.class);

        assertThat(token.value()).isEqualTo("token-123");
    }

    @Test
    void creatorValidationErrorsAreReportedFromImplicitParameterCreators() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .build();

        assertThatThrownBy(() -> mapper.readValue("{\"value\":\"   \"}", ProductCode.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasMessageContaining("Product code must not be blank");

        assertThatThrownBy(() -> mapper.readValue("{\"sku\":\"bad\",\"availableCount\":-1}", InventoryItem.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasMessageContaining("Available count must not be negative");
    }

    @Test
    void disabledBindingModeIgnoresDefaultModeAnnotatedCreator() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.DISABLED))
                .build();

        assertThatThrownBy(() -> mapper.readValue("{\"value\":\"active\"}", DisabledCreatorValue.class))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("Cannot construct instance")
                .hasMessageContaining("DisabledCreatorValue");
    }

    @Test
    void explicitParameterAnnotationsContinueToWorkWhenModuleIsRegistered() throws Exception {
        JsonMapper mapper = mapperWithDefaultModule();

        AnnotatedCreatorValue value = mapper.readValue(
                "{\"external_name\":\"visible\",\"external_count\":3}",
                AnnotatedCreatorValue.class);

        assertThat(value).isEqualTo(new AnnotatedCreatorValue("visible", 3));
    }

    @Test
    void explicitCreatorModeIsRespectedWhenDefaultBindingIsDisabled() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.DISABLED))
                .build();

        ExplicitPropertiesValue value = mapper.readValue(
                "{\"name\":\"configured\",\"count\":2}",
                ExplicitPropertiesValue.class);

        assertThat(value).isEqualTo(new ExplicitPropertiesValue("configured", 2));
    }

    @Test
    void reportsInvalidInputForImplicitParameterTypeMismatch() {
        JsonMapper mapper = mapperWithDefaultModule();

        assertThatThrownBy(() -> mapper.readValue("{\"sku\":\"A-100\",\"availableCount\":\"many\"}",
                InventoryItem.class))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("availableCount");
    }

    @Test
    void objectMapperAutoDiscoveryFindsAndRegistersParameterNamesModule() throws Exception {
        List<Module> discoveredModules = ObjectMapper.findModules();

        assertThat(discoveredModules)
                .anySatisfy(module -> assertThat(module).isInstanceOf(ParameterNamesModule.class));

        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        AutoDiscoveredValue value = mapper.readValue("{\"name\":\"service-loader\"}", AutoDiscoveredValue.class);

        assertThat(value).isEqualTo(new AutoDiscoveredValue("service-loader"));
    }

    @Test
    void moduleInstancesUseReferenceEqualityAndStableClassHashCode() {
        ParameterNamesModule defaultModule = new ParameterNamesModule();
        ParameterNamesModule anotherDefaultModule = new ParameterNamesModule();
        ParameterNamesModule propertiesModule = new ParameterNamesModule(JsonCreator.Mode.PROPERTIES);

        assertThat(defaultModule).isEqualTo(defaultModule);
        assertThat(defaultModule).isNotEqualTo(anotherDefaultModule);
        assertThat(defaultModule).isNotEqualTo(propertiesModule);
        assertThat(defaultModule.hashCode()).isEqualTo(ParameterNamesModule.class.hashCode());
        assertThat(propertiesModule.hashCode()).isEqualTo(ParameterNamesModule.class.hashCode());
    }

    private static JsonMapper mapperWithDefaultModule() {
        return JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .build();
    }

    public record Order(
            String orderId,
            BigDecimal amount,
            Address shippingAddress,
            List<LineItem> lineItems,
            Map<String, String> attributes,
            OrderStatus status) {
    }

    public record Address(String street, String city, String postalCode) {
    }

    public record LineItem(String sku, int quantity) {
    }

    public enum OrderStatus {
        DRAFT,
        CONFIRMED,
        CANCELLED
    }

    public record CustomerName(String firstName, String lastName) {
        @JsonCreator
        public CustomerName {
        }
    }

    public record InventoryItem(String sku, int availableCount) {
        @JsonCreator
        public InventoryItem {
            if (availableCount < 0) {
                throw new IllegalArgumentException("Available count must not be negative");
            }
        }
    }

    public record ProductCode(String value) {
        @JsonCreator
        public ProductCode {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Product code must not be blank");
            }
        }
    }

    public static final class ScalarToken {
        private final String value;

        @JsonCreator
        public ScalarToken(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static final class DisabledCreatorValue {
        private final String value;

        @JsonCreator
        public DisabledCreatorValue(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public record AnnotatedCreatorValue(String name, int count) {
        @JsonCreator
        public AnnotatedCreatorValue(
                @JsonProperty("external_name") String name,
                @JsonProperty("external_count") int count) {
            this.name = name;
            this.count = count;
        }
    }

    public record ExplicitPropertiesValue(String name, int count) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public ExplicitPropertiesValue {
        }
    }

    public record AutoDiscoveredValue(String name) {
        @JsonCreator
        public AutoDiscoveredValue {
        }
    }
}
