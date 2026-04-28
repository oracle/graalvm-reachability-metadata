/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ser.std.StdSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jackson_databindTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTripsRecordWithNestedCollectionsEnumsNumbersAndOptionalValues() throws JacksonException {
        Order order = new Order("order-1", OrderStatus.IN_PROGRESS,
                List.of(new OrderLine("sku-1", 2, new BigDecimal("19.95")),
                        new OrderLine("sku-2", 1, new BigDecimal("5.50"))),
                Map.of("customer", "Ada", "channel", "web"), Optional.of("gift wrap"), OptionalInt.of(5));

        String json = MAPPER.writeValueAsString(order);
        Order roundTripped = MAPPER.readValue(json, Order.class);

        assertThat(roundTripped).isEqualTo(order);
        assertThat(roundTripped.note()).contains("gift wrap");
        assertThat(roundTripped.priority().isPresent()).isTrue();
        assertThat(roundTripped.priority().getAsInt()).isEqualTo(5);
    }

    @Test
    void readsNestedPayloadsWithObjectReaderAndWritesPrettyJsonWithObjectWriter() throws JacksonException {
        String json = """
                {
                  "metadata": {"source": "warehouse"},
                  "payload": {
                    "lines": [
                      {"sku":"paper","quantity":3,"unitPrice":1.25},
                      {"sku":"pen","quantity":4,"unitPrice":2.50}
                    ]
                  }
                }
                """;

        List<OrderLine> lines = MAPPER.readerForListOf(OrderLine.class).at("/payload/lines").readValue(json);
        String prettyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(lines);

        assertThat(lines).containsExactly(new OrderLine("paper", 3, new BigDecimal("1.25")),
                new OrderLine("pen", 4, new BigDecimal("2.50")));
        assertThat(prettyJson).contains(System.lineSeparator()).contains("\"sku\" : \"paper\"");
    }

    @Test
    void updatesExistingBeanInstanceThroughReaderForUpdating() throws JacksonException {
        MutableProfile profile = new MutableProfile();
        profile.setName("initial");
        profile.setScore(7);
        profile.setActive(false);

        MutableProfile updated = MAPPER.readerForUpdating(profile).readValue("{"
                + "\"score\": 11,"
                + "\"active\": true"
                + "}");

        assertThat(updated).isSameAs(profile);
        assertThat(updated.getName()).isEqualTo("initial");
        assertThat(updated.getScore()).isEqualTo(11);
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void treeModelSupportsMutationNavigationConversionAndRemoval() throws JacksonException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("status", "ok");
        ObjectNode summary = root.putObject("summary");
        summary.put("count", 2);
        ArrayNode items = root.putArray("items");
        items.addObject().put("sku", "paper").put("quantity", 3).put("unitPrice", 1.25);
        items.addObject().put("sku", "pen").put("quantity", 4).put("unitPrice", 2.50);

        JsonNode firstSku = root.at("/items/0/sku");
        root.without("status");
        OrderLine firstLine = MAPPER.treeToValue(root.at("/items/0"), OrderLine.class);
        JsonNode tree = MAPPER.readTree(MAPPER.writeValueAsString(root));

        assertThat(firstSku.asText()).isEqualTo("paper");
        assertThat(root.has("status")).isFalse();
        assertThat(firstLine).isEqualTo(new OrderLine("paper", 3, new BigDecimal("1.25")));
        assertThat(tree.at("/summary/count").asInt()).isEqualTo(2);
        assertThat(tree.path("missing").isMissingNode()).isTrue();
    }

    @Test
    void customModuleSerializesAndDeserializesDomainValueObjects() throws JacksonException {
        SimpleModule moneyModule = new SimpleModule("money-module");
        moneyModule.addSerializer(Money.class, new MoneySerializer());
        moneyModule.addDeserializer(Money.class, new MoneyDeserializer());
        ObjectMapper mapper = JsonMapper.builder().addModule(moneyModule).build();

        Invoice invoice = new Invoice("invoice-1", new Money("USD", new BigDecimal("42.75")));
        String json = mapper.writeValueAsString(invoice);
        Invoice roundTripped = mapper.readValue(json, Invoice.class);

        assertThat(json).contains("\"total\":\"USD 42.75\"");
        assertThat(roundTripped).isEqualTo(invoice);
    }

    @Test
    void builderConfigurationAppliesNamingStrategyAndNumericCoercion() throws JacksonException {
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();

        MetricReading reading = mapper.readValue("{"
                + "\"sensor_id\": \"sensor-A\","
                + "\"observed_value\": 12.5,"
                + "\"healthy\": true"
                + "}", MetricReading.class);
        String readingJson = mapper.writeValueAsString(reading);
        Map<String, Object> numbers = mapper.readValue("{\"b\": 1.25, \"a\": 2}",
                new TypeReference<LinkedHashMap<String, Object>>() { });
        String orderedJson = mapper.writeValueAsString(numbers);

        assertThat(reading.getSensorId()).isEqualTo("sensor-A");
        assertThat(reading.getObservedValue()).isEqualByComparingTo("12.5");
        assertThat(readingJson).contains("\"sensor_id\":").contains("\"observed_value\":12.5");
        assertThat(numbers.get("b")).isInstanceOf(BigDecimal.class);
        assertThat(orderedJson).isEqualTo("{\"a\":2,\"b\":1.25}");
    }

    @Test
    void mixInsApplyExternalPropertyAnnotationsWithoutChangingTargetType() throws JacksonException {
        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(ExternalAccount.class, ExternalAccountMixin.class)
                .build();
        ExternalAccount account = new ExternalAccount("ada", "token-123", true);

        String json = mapper.writeValueAsString(account);
        ExternalAccount restored = mapper.readValue("{"
                + "\"login_name\": \"grace\","
                + "\"apiToken\": \"ignored-token\","
                + "\"enabled\": false"
                + "}", ExternalAccount.class);

        assertThat(json).contains("\"login_name\":\"ada\"")
                .contains("\"enabled\":true")
                .doesNotContain("apiToken")
                .doesNotContain("loginName");
        assertThat(restored.getLoginName()).isEqualTo("grace");
        assertThat(restored.getApiToken()).isNull();
        assertThat(restored.isEnabled()).isFalse();
    }

    public record Order(String id, OrderStatus status, List<OrderLine> lines, Map<String, String> attributes,
            Optional<String> note, OptionalInt priority) {
    }

    public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {
    }

    public enum OrderStatus {
        NEW,
        IN_PROGRESS,
        COMPLETE
    }

    public record Invoice(String id, Money total) {
    }

    public record Money(String currency, BigDecimal amount) {
    }

    public static final class MoneySerializer extends StdSerializer<Money> {
        public MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value.currency() + " " + value.amount());
        }
    }

    public static final class MoneyDeserializer extends StdDeserializer<Money> {
        public MoneyDeserializer() {
            super(Money.class);
        }

        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            String[] parts = parser.getValueAsString().split(" ", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }
    }

    public static final class MutableProfile {
        private String name;
        private int score;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static final class MetricReading {
        private String sensorId;
        private BigDecimal observedValue;
        private boolean healthy;

        public String getSensorId() {
            return sensorId;
        }

        public void setSensorId(String sensorId) {
            this.sensorId = sensorId;
        }

        public BigDecimal getObservedValue() {
            return observedValue;
        }

        public void setObservedValue(BigDecimal observedValue) {
            this.observedValue = observedValue;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }

    public abstract static class ExternalAccountMixin {
        @JsonProperty("login_name")
        public abstract String getLoginName();

        @JsonProperty("login_name")
        public abstract void setLoginName(String loginName);

        @JsonIgnore
        public abstract String getApiToken();

        @JsonIgnore
        public abstract void setApiToken(String apiToken);
    }

    public static final class ExternalAccount {
        private String loginName;
        private String apiToken;
        private boolean enabled;

        public ExternalAccount() {
        }

        public ExternalAccount(String loginName, String apiToken, boolean enabled) {
            this.loginName = loginName;
            this.apiToken = apiToken;
            this.enabled = enabled;
        }

        public String getLoginName() {
            return loginName;
        }

        public void setLoginName(String loginName) {
            this.loginName = loginName;
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
