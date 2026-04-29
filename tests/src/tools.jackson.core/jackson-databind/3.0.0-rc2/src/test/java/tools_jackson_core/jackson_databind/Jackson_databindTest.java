/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jackson_databindTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void annotatedBeanRoundTripHonorsAliasesInclusionAndIgnoredProperties() throws Exception {
        String json = """
                {
                  "order_id": "A-100",
                  "customer_name": "Ada",
                  "lineItems": {"espresso": 2.50, "croissant": 4.25},
                  "delivery": {"street": "Main Street", "city": "Belgrade"},
                  "metadata": {"priority": true, "table": 7},
                  "internalToken": "not-for-api"
                }
                """;

        CoffeeOrder order = MAPPER.readValue(json, CoffeeOrder.class);

        assertThat(order.id).isEqualTo("A-100");
        assertThat(order.customer).isEqualTo("Ada");
        assertThat(order.lineItems).containsEntry("espresso", new BigDecimal("2.50"));
        assertThat(order.delivery.city).isEqualTo("Belgrade");
        assertThat(order.metadata).containsEntry("priority", true);
        assertThat(order.internalToken).isEqualTo("generated-only");

        String serialized = MAPPER.writeValueAsString(order);
        JsonNode serializedTree = MAPPER.readTree(serialized);

        assertThat(serializedTree.get("order_id").asText()).isEqualTo("A-100");
        assertThat(serializedTree.get("customer").asText()).isEqualTo("Ada");
        assertThat(serializedTree.has("notes")).isFalse();
        assertThat(serializedTree.has("internalToken")).isFalse();
    }

    @Test
    void namingStrategyAndCaseInsensitiveEnumMappingWorkTogether() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();

        DrinkPreference preference = mapper.readValue("""
                {
                  "drink_size": "large",
                  "bean_origin": "Ethiopia",
                  "serving_temperature": 68
                }
                """, DrinkPreference.class);

        assertThat(preference.drinkSize).isEqualTo(DrinkSize.LARGE);
        assertThat(preference.beanOrigin).isEqualTo("Ethiopia");
        assertThat(preference.servingTemperature).isEqualTo(68);
        JsonNode serializedPreference = mapper.readTree(mapper.writeValueAsString(preference));
        assertThat(serializedPreference.get("drink_size").asText()).isEqualTo("LARGE");
        assertThat(serializedPreference.get("bean_origin").asText()).isEqualTo("Ethiopia");
        assertThat(serializedPreference.get("serving_temperature").intValue()).isEqualTo(68);
    }

    @Test
    void genericReadersAndWritersPreserveNestedContainerTypes() throws Exception {
        CoffeeOrder first = coffeeOrder("A-101", "Grace", "latte", "Tirana");
        CoffeeOrder second = coffeeOrder("A-102", "Linus", "tea", "Helsinki");
        List<CoffeeOrder> orders = List.of(first, second);

        ObjectWriter writer = MAPPER.writerFor(new TypeReference<List<CoffeeOrder>>() {});
        String json = writer.writeValueAsString(orders);

        ObjectReader reader = MAPPER.readerFor(new TypeReference<List<CoffeeOrder>>() {});
        List<CoffeeOrder> restored = reader.readValue(json);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).lineItems).containsEntry("latte", new BigDecimal("3.40"));
        assertThat(restored.get(1).delivery.city).isEqualTo("Helsinki");

        JavaType valueType = MAPPER.getTypeFactory().constructParametricType(ApiEnvelope.class, CoffeeOrder.class);
        JavaType mapType = MAPPER.getTypeFactory()
                .constructMapType(LinkedHashMap.class, MAPPER.constructType(String.class), valueType);
        String envelopesJson = """
                {
                  "first": {"status": "ok", "payload": {"order_id": "A-103", "customer": "Katherine"}},
                  "second": {"status": "queued", "payload": {"order_id": "A-104", "customer": "Margaret"}}
                }
                """;
        Map<String, ApiEnvelope<CoffeeOrder>> envelopes = MAPPER.readValue(envelopesJson, mapType);

        assertThat(envelopes.get("first").payload.id).isEqualTo("A-103");
        assertThat(envelopes.get("second").payload.customer).isEqualTo("Margaret");
    }

    @Test
    void treeModelSupportsMutationPointersAndPojoConversion() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("order_id", "A-105");
        root.put("customer", "Barbara");
        ObjectNode delivery = root.putObject("delivery");
        delivery.put("street", "Fifth Avenue");
        delivery.put("city", "New York");
        ArrayNode notes = root.putArray("notes");
        notes.add("decaf");
        notes.add("with oat milk");

        assertThat(root.at("/delivery/city").asText()).isEqualTo("New York");
        assertThat(root.findValuesAsString("customer")).containsExactly("Barbara");

        CoffeeOrder order = MAPPER.treeToValue(root, CoffeeOrder.class);
        JsonNode convertedBack = MAPPER.valueToTree(order);

        assertThat(order.notes).containsExactly("decaf", "with oat milk");
        assertThat(convertedBack.at("/delivery/street").asText()).isEqualTo("Fifth Avenue");
        assertThat(convertedBack.get("notes").get(1).asText()).isEqualTo("with oat milk");
    }

    @Test
    void updateReaderAndConversionModifyExistingObjectsWithoutLosingState() throws Exception {
        CoffeeOrder order = coffeeOrder("A-106", "Edsger", "mocha", "Amsterdam");
        order.notes = new ArrayList<>(List.of("window seat"));

        CoffeeOrder updated = MAPPER.readerForUpdating(order).readValue("""
                {
                  "customer": "Evelyn",
                  "delivery": {"city": "London"},
                  "notes": ["quiet corner", "no sugar"]
                }
                """);

        assertThat(updated).isSameAs(order);
        assertThat(updated.id).isEqualTo("A-106");
        assertThat(updated.customer).isEqualTo("Evelyn");
        assertThat(updated.delivery.city).isEqualTo("London");
        assertThat(updated.notes).containsExactly("quiet corner", "no sugar");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("status", "converted");
        raw.put("payload", Map.of("order_id", "A-107", "customer", "Frances"));
        ApiEnvelope<CoffeeOrder> envelope = MAPPER.convertValue(raw,
                new TypeReference<ApiEnvelope<CoffeeOrder>>() {});

        assertThat(envelope.status).isEqualTo("converted");
        assertThat(envelope.payload.id).isEqualTo("A-107");
        assertThat(envelope.payload.customer).isEqualTo("Frances");
    }

    @Test
    void strictAndLenientDeserializationFeaturesAreConfigurable() throws Exception {
        ObjectMapper strictMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        ObjectMapper lenientMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .build();

        assertThatThrownBy(() -> strictMapper.readValue("""
                {"order_id": "A-108", "customer": "Hedy", "unexpected": true}
                """, CoffeeOrder.class))
                .hasMessageContaining("unexpected");

        CoffeeOrder order = lenientMapper.readValue("""
                {"order_id": "A-108", "customer": "Hedy", "notes": "ring bell", "unexpected": true}
                """, CoffeeOrder.class);

        assertThat(order.notes).containsExactly("ring bell");
    }

    @Test
    void polymorphicTypeInformationSelectsConcreteSubtypes() throws Exception {
        List<PaymentMethod> methods = List.of(
                new CreditCard("visa", "1234"),
                new GiftCard("GIFT-7", new BigDecimal("15.00")));

        String json = MAPPER.writerFor(new TypeReference<List<PaymentMethod>>() {}).writeValueAsString(methods);
        List<PaymentMethod> restored = MAPPER.readValue(json, new TypeReference<List<PaymentMethod>>() {});

        assertThat(MAPPER.readTree(json).get(0).get("kind").asText()).isEqualTo("card");
        assertThat(restored).hasSize(2);
        assertThat(restored.get(0)).isInstanceOfSatisfying(CreditCard.class, card -> {
            assertThat(card.network).isEqualTo("visa");
            assertThat(card.lastFour).isEqualTo("1234");
        });
        assertThat(restored.get(1)).isInstanceOfSatisfying(GiftCard.class, giftCard -> {
            assertThat(giftCard.code).isEqualTo("GIFT-7");
            assertThat(giftCard.balance).isEqualByComparingTo("15.00");
        });
    }

    @Test
    void recordsOptionalsAndPrettyPrintingUseBuiltInSerializers() throws Exception {
        Receipt receipt = new Receipt(
                "R-1",
                Optional.of(new Money(new BigDecimal("9.99"), CurrencyCode.EUR)),
                List.of(new Money(new BigDecimal("2.50"), CurrencyCode.USD)));
        ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();

        String json = mapper.writeValueAsString(receipt);
        Receipt restored = mapper.readValue(json, Receipt.class);

        assertThat(json).contains(System.lineSeparator());
        assertThat(restored.id()).isEqualTo("R-1");
        assertThat(restored.discount()).hasValue(new Money(new BigDecimal("9.99"), CurrencyCode.EUR));
        assertThat(restored.payments()).containsExactly(new Money(new BigDecimal("2.50"), CurrencyCode.USD));
    }

    private static CoffeeOrder coffeeOrder(String id, String customer, String itemName, String city) {
        CoffeeOrder order = new CoffeeOrder();
        order.id = id;
        order.customer = customer;
        order.delivery = new Address("Main Street", city);
        order.lineItems = new LinkedHashMap<>();
        order.lineItems.put(itemName, new BigDecimal("3.40"));
        order.metadata = new LinkedHashMap<>();
        order.metadata.put("takeaway", false);
        return order;
    }

    @JsonPropertyOrder({"order_id", "customer", "lineItems", "delivery", "notes", "metadata"})
    public static class CoffeeOrder {
        @JsonProperty("order_id")
        public String id;

        @JsonAlias("customer_name")
        public String customer;

        public Map<String, BigDecimal> lineItems = new LinkedHashMap<>();

        public Address delivery;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> notes = new ArrayList<>();

        public Map<String, Object> metadata = new LinkedHashMap<>();

        @JsonIgnore
        public String internalToken = "generated-only";
    }

    public static class Address {
        public String street;
        public String city;

        public Address() {
        }

        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class DrinkPreference {
        public DrinkSize drinkSize;
        public String beanOrigin;
        public int servingTemperature;
    }

    public enum DrinkSize {
        SMALL,
        MEDIUM,
        LARGE
    }

    public static class ApiEnvelope<T> {
        public String status;
        public T payload;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CreditCard.class, name = "card"),
            @JsonSubTypes.Type(value = GiftCard.class, name = "gift")
    })
    public interface PaymentMethod {
    }

    public static class CreditCard implements PaymentMethod {
        public String network;
        public String lastFour;

        public CreditCard() {
        }

        public CreditCard(String network, String lastFour) {
            this.network = network;
            this.lastFour = lastFour;
        }
    }

    public static class GiftCard implements PaymentMethod {
        public String code;
        public BigDecimal balance;

        public GiftCard() {
        }

        public GiftCard(String code, BigDecimal balance) {
            this.code = code;
            this.balance = balance;
        }
    }

    public record Receipt(String id, Optional<Money> discount, List<Money> payments) {
    }

    public record Money(BigDecimal amount, CurrencyCode currency) {
    }

    public enum CurrencyCode {
        EUR,
        USD
    }
}
