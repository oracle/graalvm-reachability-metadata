/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_core.jackson_databind;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.ser.std.StdSerializer;

public class Jackson_databindTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void mapsAnnotatedImmutableBeanAndAppliesNamingStrategy() throws Exception {
        ObjectMapper snakeCaseMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        Customer customer = snakeCaseMapper.readValue(
                """
                {
                  "id": 7,
                  "first_name": "Ada",
                  "last_name": "Lovelace",
                  "ignored_by_annotation": "not visible",
                  "tags": ["founder", "vip"]
                }
                """,
                Customer.class);

        assertThat(customer.getId()).isEqualTo(7);
        assertThat(customer.getFirstName()).isEqualTo("Ada");
        assertThat(customer.getLastName()).isEqualTo("Lovelace");
        assertThat(customer.getTags()).containsExactly("founder", "vip");

        JsonNode serialized = snakeCaseMapper.readTree(snakeCaseMapper.writeValueAsString(customer));
        assertThat(serialized.get("first_name").asText()).isEqualTo("Ada");
        assertThat(serialized.get("last_name").asText()).isEqualTo("Lovelace");
        assertThat(serialized.has("ignored_by_annotation")).isFalse();
    }

    @Test
    void bindsGenericCollectionsAndConvertsBetweenTreesAndValues() throws Exception {
        ObjectNode orderTree = mapper.createObjectNode();
        orderTree.put("orderId", "A-100");
        orderTree.putObject("shipTo")
                .put("city", "London")
                .put("postalCode", "SW1A 1AA");
        ArrayNode lines = orderTree.putArray("lines");
        lines.addObject().put("sku", "book").put("quantity", 2);
        lines.addObject().put("sku", "pen").put("quantity", 5);

        Order order = mapper.treeToValue(orderTree, Order.class);
        assertThat(order.orderId()).isEqualTo("A-100");
        assertThat(order.shipTo().city()).isEqualTo("London");
        assertThat(order.lines()).containsExactly(new OrderLine("book", 2), new OrderLine("pen", 5));

        Map<String, List<OrderLine>> grouped = mapper.readValue(
                """
                {
                  "office": [ { "sku": "staples", "quantity": 3 } ],
                  "home": [ { "sku": "paper", "quantity": 10 } ]
                }
                """,
                new TypeReference<Map<String, List<OrderLine>>>() {
                });
        assertThat(grouped).containsOnlyKeys("office", "home");
        assertThat(grouped.get("office")).containsExactly(new OrderLine("staples", 3));

        JsonNode roundTrippedTree = mapper.valueToTree(order);
        assertThat(roundTrippedTree.at("/shipTo/postalCode").asText()).isEqualTo("SW1A 1AA");
        assertThat(roundTrippedTree.findValue("sku").asText()).isEqualTo("book");
    }

    @Test
    void handlesAnnotatedPolymorphicTypesInTypedContainers() throws Exception {
        Drawing drawing = new Drawing(List.of(
                new Circle("sun", 2.5D),
                new Rectangle("window", 4.0D, 3.0D)));

        String json = mapper.writeValueAsString(drawing);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.at("/shapes/0/type").asText()).isEqualTo("circle");
        assertThat(tree.at("/shapes/1/type").asText()).isEqualTo("rectangle");

        Drawing restored = mapper.readValue(json, Drawing.class);
        assertThat(restored.shapes()).hasSize(2);
        assertThat(restored.shapes().get(0)).isInstanceOfSatisfying(Circle.class, circle -> {
            assertThat(circle.getName()).isEqualTo("sun");
            assertThat(circle.getRadius()).isEqualTo(2.5D);
        });
        assertThat(restored.shapes().get(1)).isInstanceOfSatisfying(Rectangle.class, rectangle -> {
            assertThat(rectangle.getName()).isEqualTo("window");
            assertThat(rectangle.getWidth()).isEqualTo(4.0D);
            assertThat(rectangle.getHeight()).isEqualTo(3.0D);
        });
    }

    @Test
    void usesCustomModuleForDomainSpecificScalarRepresentation() throws Exception {
        SimpleModule moneyModule = new SimpleModule("money-module")
                .addSerializer(Money.class, new MoneySerializer())
                .addDeserializer(Money.class, new MoneyDeserializer());
        ObjectMapper moneyMapper = JsonMapper.builder()
                .addModule(moneyModule)
                .build();

        Invoice invoice = new Invoice("INV-1", new Money("EUR", new BigDecimal("19.95")));
        String json = moneyMapper.writeValueAsString(invoice);
        assertThat(moneyMapper.readTree(json).get("total").asText()).isEqualTo("EUR 19.95");

        Invoice restored = moneyMapper.readValue(json, Invoice.class);
        assertThat(restored.id()).isEqualTo("INV-1");
        assertThat(restored.total()).isEqualTo(new Money("EUR", new BigDecimal("19.95")));
    }

    @Test
    void deserializesValueObjectsThroughAnnotatedBuilders() throws Exception {
        ProductCatalog catalog = mapper.readValue(
                """
                {
                  "name": "featured",
                  "items": [
                    { "code": "BK-1", "displayName": "Graph Theory", "available": true },
                    { "code": "PN-2", "displayName": "Fountain Pen", "available": false }
                  ]
                }
                """,
                ProductCatalog.class);

        assertThat(catalog.getName()).isEqualTo("featured");
        assertThat(catalog.getItems()).hasSize(2);
        assertThat(catalog.getItems().get(0).getCode()).isEqualTo("BK-1");
        assertThat(catalog.getItems().get(0).getDisplayName()).isEqualTo("Graph Theory");
        assertThat(catalog.getItems().get(0).isAvailable()).isTrue();
        assertThat(catalog.getItems().get(1).getCode()).isEqualTo("PN-2");
        assertThat(catalog.getItems().get(1).getDisplayName()).isEqualTo("Fountain Pen");
        assertThat(catalog.getItems().get(1).isAvailable()).isFalse();
    }

    @Test
    void objectReadersAndWritersCanTargetSubtreesViewsAndExistingValues() throws Exception {
        ObjectMapper viewMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();

        UserAccount account = new UserAccount("ada", "ada@example.test", "secret-token");
        String publicJson = viewMapper.writerWithView(PublicView.class).writeValueAsString(account);
        assertThat(publicJson).contains("\n");
        JsonNode publicTree = viewMapper.readTree(publicJson);
        assertThat(publicTree.has("username")).isTrue();
        assertThat(publicTree.has("email")).isTrue();
        assertThat(publicTree.has("internalToken")).isFalse();

        OrderLine selectedLine = mapper.readerFor(OrderLine.class)
                .at("/payload/lines/1")
                .readValue(
                        """
                        {
                          "payload": {
                            "lines": [
                              { "sku": "notebook", "quantity": 1 },
                              { "sku": "pencil", "quantity": 12 }
                            ]
                          }
                        }
                        """);
        assertThat(selectedLine).isEqualTo(new OrderLine("pencil", 12));

        MutableProfile profile = new MutableProfile();
        profile.setName("Grace");
        profile.setAttributes(new LinkedHashMap<>(Map.of("role", "admin")));
        MutableProfile updated = mapper.readerForUpdating(profile).readValue(
                """
                {
                  "name": "Grace Hopper",
                  "attributes": { "language": "COBOL" }
                }
                """);
        assertThat(updated).isSameAs(profile);
        assertThat(updated.getName()).isEqualTo("Grace Hopper");
        assertThat(updated.getAttributes()).containsEntry("language", "COBOL");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Customer {
        private final int id;
        private final String firstName;
        private final String lastName;
        private final List<String> tags;

        @JsonCreator
        public Customer(
                @JsonProperty("id") int id,
                @JsonProperty("first_name") String firstName,
                @JsonProperty("last_name") String lastName,
                @JsonProperty("tags") List<String> tags) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.tags = List.copyOf(tags);
        }

        public int getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public List<String> getTags() {
            return tags;
        }

        @JsonIgnore
        public String getIgnoredByAnnotation() {
            return "computed";
        }
    }

    public record Address(String city, String postalCode) {
    }

    public record Order(String orderId, Address shipTo, List<OrderLine> lines) {
    }

    public record OrderLine(String sku, int quantity) {
    }

    public record Drawing(List<Shape> shapes) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Circle.class, name = "circle"),
            @JsonSubTypes.Type(value = Rectangle.class, name = "rectangle")
    })
    public interface Shape {
        String getName();
    }

    public static final class Circle implements Shape {
        private final String name;
        private final double radius;

        @JsonCreator
        public Circle(@JsonProperty("name") String name, @JsonProperty("radius") double radius) {
            this.name = name;
            this.radius = radius;
        }

        @Override
        public String getName() {
            return name;
        }

        public double getRadius() {
            return radius;
        }
    }

    public static final class Rectangle implements Shape {
        private final String name;
        private final double width;
        private final double height;

        @JsonCreator
        public Rectangle(
                @JsonProperty("name") String name,
                @JsonProperty("width") double width,
                @JsonProperty("height") double height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }

        @Override
        public String getName() {
            return name;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }
    }

    public record Invoice(String id, Money total) {
    }

    @JsonDeserialize(builder = ProductCatalog.Builder.class)
    public static final class ProductCatalog {
        private final String name;
        private final List<CatalogItem> items;

        private ProductCatalog(Builder builder) {
            this.name = builder.name;
            this.items = List.copyOf(builder.items);
        }

        public String getName() {
            return name;
        }

        public List<CatalogItem> getItems() {
            return items;
        }

        @JsonPOJOBuilder(withPrefix = "set")
        public static final class Builder {
            private String name;
            private List<CatalogItem> items = List.of();

            public Builder setName(String name) {
                this.name = name;
                return this;
            }

            public Builder setItems(List<CatalogItem> items) {
                this.items = List.copyOf(items);
                return this;
            }

            public ProductCatalog build() {
                return new ProductCatalog(this);
            }
        }
    }

    @JsonDeserialize(builder = CatalogItem.Builder.class)
    public static final class CatalogItem {
        private final String code;
        private final String displayName;
        private final boolean available;

        private CatalogItem(Builder builder) {
            this.code = builder.code;
            this.displayName = builder.displayName;
            this.available = builder.available;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isAvailable() {
            return available;
        }

        @JsonPOJOBuilder(buildMethodName = "create", withPrefix = "apply")
        public static final class Builder {
            private String code;
            private String displayName;
            private boolean available;

            public Builder applyCode(String code) {
                this.code = code;
                return this;
            }

            public Builder applyDisplayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            public Builder applyAvailable(boolean available) {
                this.available = available;
                return this;
            }

            public CatalogItem create() {
                return new CatalogItem(this);
            }
        }
    }

    public static final class Money {
        private final String currency;
        private final BigDecimal amount;

        public Money(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Money money)) {
                return false;
            }
            return Objects.equals(currency, money.currency) && Objects.equals(amount, money.amount);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currency, amount);
        }
    }

    public static final class MoneySerializer extends StdSerializer<Money> {
        public MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money value, JsonGenerator generator, SerializationContext context) throws JacksonException {
            generator.writeString(value.getCurrency() + " " + value.getAmount().toPlainString());
        }
    }

    public static final class MoneyDeserializer extends ValueDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            String[] parts = parser.getText().split(" ", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }
    }

    public interface PublicView {
    }

    public interface InternalView extends PublicView {
    }

    public static final class UserAccount {
        private final String username;
        private final String email;
        private final String internalToken;

        @JsonCreator
        public UserAccount(
                @JsonProperty("username") String username,
                @JsonProperty("email") String email,
                @JsonProperty("internalToken") String internalToken) {
            this.username = username;
            this.email = email;
            this.internalToken = internalToken;
        }

        @JsonView(PublicView.class)
        public String getUsername() {
            return username;
        }

        @JsonView(PublicView.class)
        public String getEmail() {
            return email;
        }

        @JsonView(InternalView.class)
        public String getInternalToken() {
            return internalToken;
        }
    }

    public static final class MutableProfile {
        private String name;
        private Map<String, String> attributes = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = new LinkedHashMap<>(attributes);
        }
    }
}
