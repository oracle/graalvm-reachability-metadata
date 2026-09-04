/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_serde.micronaut_serde_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.Test;

public class MicronautSerdeJacksonTest {

    @Test
    void roundTripsSerdeableObjectGraphWithJacksonAnnotations() throws Exception {
        Purchase purchase = samplePurchase();

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(purchase);

            assertThat(json)
                    .contains("\"purchase_id\":\"P-100\"")
                    .contains("\"full_name\":\"Ada Lovelace\"")
                    .contains("\"description\":\"Notebook \\\"Pro\\\"\"")
                    .doesNotContain("searchKey", "internalCode", "comment");

            Purchase decoded = mapper.readValue(json, Purchase.class);
            assertPurchase(decoded);
        }
    }

    @Test
    void readsAndWritesGenericCollectionsUsingArguments() throws Exception {
        List<LineItem> items = List.of(
                new LineItem("SKU-1", "Notebook", 2),
                new LineItem("SKU-2", "Pencil", 5));
        Map<String, LineItem> itemsBySku = new LinkedHashMap<>();
        itemsBySku.put("first", items.get(0));
        itemsBySku.put("second", items.get(1));

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Argument<List<LineItem>> listType = Argument.listOf(LineItem.class);
            Argument<Map<String, LineItem>> mapType = Argument.mapOf(String.class, LineItem.class);

            String listJson = mapper.writeValueAsString(listType, items);
            List<LineItem> decodedItems = mapper.readValue(listJson, listType);
            assertThat(decodedItems).containsExactlyElementsOf(items);

            String mapJson = mapper.writeValueAsString(mapType, itemsBySku);
            Map<String, LineItem> decodedMap = mapper.readValue(mapJson, mapType);
            assertThat(decodedMap).containsExactlyInAnyOrderEntriesOf(itemsBySku);
        }
    }

    @Test
    void supportsTypedByteStreamAndTreeOperations() throws Exception {
        Purchase purchase = samplePurchase();
        Argument<Purchase> purchaseType = Argument.of(Purchase.class);

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonMapper specificMapper = mapper.createSpecific(purchaseType);

            byte[] bytes = specificMapper.writeValueAsBytes(purchaseType, purchase);
            assertPurchase(specificMapper.readValue(bytes, purchaseType));

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                mapper.writeValue(output, purchaseType, purchase);
                try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
                    assertPurchase(mapper.readValue(input, purchaseType));
                }
            }

            JsonNode tree = mapper.writeValueToTree(purchaseType, purchase);
            assertPurchase(mapper.readValueFromTree(tree, purchaseType));
            assertThat(mapper.writeValueAsString(null)).isEqualTo("null");
            assertThat(mapper.readValue("null", Purchase.class)).isNull();
        }
    }

    @Test
    void handlesNamedPolymorphicValues() throws Exception {
        Argument<Notification> notificationType = Argument.of(Notification.class);
        Notification notification = new EmailNotification("ops@example.test", "Build complete");

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(notificationType, notification);
            assertThat(json).contains("\"kind\":\"email\"");

            Notification decoded = mapper.readValue(json, notificationType);
            assertThat(decoded).isEqualTo(notification);
        }
    }

    @Test
    void appliesSerializationViews() throws Exception {
        Account account = new Account("reader", "token-123");

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonMapper publicMapper = mapper.cloneWithViewClass(PublicView.class);
            JsonMapper internalMapper = mapper.cloneWithViewClass(InternalView.class);

            assertThat(publicMapper.writeValueAsString(account))
                    .contains("\"username\":\"reader\"")
                    .doesNotContain("accessToken", "token-123");
            assertThat(internalMapper.writeValueAsString(account))
                    .contains("\"username\":\"reader\"")
                    .contains("\"accessToken\":\"token-123\"");
        }
    }

    @Test
    void readsUnquotedPropertyNamesWhenJacksonReadFeatureIsEnabled() throws Exception {
        Map<String, Object> properties = Map.of(
                "micronaut.serde.jackson.json-read-features.ALLOW_UNQUOTED_PROPERTY_NAMES", "true");

        try (ApplicationContext context = ApplicationContext.run(properties)) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            FeatureMessage message = mapper.readValue(
                    "{subject:\"Deployment ready\",priority:3}", FeatureMessage.class);

            assertThat(message).isEqualTo(new FeatureMessage("Deployment ready", 3));
        }
    }

    @Test
    void writesUnquotedPropertyNamesWhenJacksonWriteFeatureIsEnabled() throws Exception {
        Map<String, Object> properties = Map.of(
                "micronaut.serde.jackson.json-write-features.QUOTE_PROPERTY_NAMES", "false");

        try (ApplicationContext context = ApplicationContext.run(properties)) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            String json = mapper.writeValueAsString(new FeatureMessage("Deployment ready", 3));

            assertThat(json).isEqualTo("{subject:\"Deployment ready\",priority:3}");
        }
    }

    @Test
    void updatesMutableValuesAndMergesAnnotatedMaps() throws Exception {
        UserPreferences preferences = new UserPreferences();
        preferences.setDisplayName("Ada");
        preferences.setSettings(new LinkedHashMap<>(Map.of(
                "language", "en",
                "theme", "dark")));

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            UserPreferences updated = mapper.updateValue(
                    preferences,
                    Argument.of(UserPreferences.class),
                    """
                    {
                      "displayName": "Grace",
                      "settings": {
                        "theme": "light",
                        "density": "compact"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            assertThat(updated).isSameAs(preferences);
            assertThat(updated.getDisplayName()).isEqualTo("Grace");
            assertThat(updated.getSettings()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "language", "en",
                    "theme", "light",
                    "density", "compact"));
        }
    }

    private static Purchase samplePurchase() {
        return new Purchase(
                "P-100",
                new Buyer("Ada Lovelace", new Address("London", "SW1A")),
                List.of(
                        new LineItem("SKU-1", "Notebook \"Pro\"", 2),
                        new LineItem("SKU-2", "Pencil", 5)),
                Map.of("primary", 12, "overflow", 4),
                PurchaseStatus.CONFIRMED,
                null,
                "audit-only");
    }

    private static void assertPurchase(Purchase purchase) {
        assertThat(purchase).isNotNull();
        assertThat(purchase.id()).isEqualTo("P-100");
        assertThat(purchase.buyer().getName()).isEqualTo("Ada Lovelace");
        assertThat(purchase.buyer().getAddress()).isEqualTo(new Address("London", "SW1A"));
        assertThat(purchase.buyer().getSearchKey()).isEqualTo("ada lovelace");
        assertThat(purchase.items()).containsExactly(
                new LineItem("SKU-1", "Notebook \"Pro\"", 2),
                new LineItem("SKU-2", "Pencil", 5));
        assertThat(purchase.stock()).containsExactlyInAnyOrderEntriesOf(Map.of("primary", 12, "overflow", 4));
        assertThat(purchase.status()).isEqualTo(PurchaseStatus.CONFIRMED);
        assertThat(purchase.comment()).isNull();
        assertThat(purchase.internalCode()).isNull();
    }

    @Serdeable
    public record Address(String city, String postalCode) {
    }

    @Serdeable
    public static final class Buyer {

        private final String name;
        private final Address address;

        @JsonCreator
        public Buyer(
                @JsonProperty("full_name") String name,
                @JsonProperty("address") Address address) {
            this.name = name;
            this.address = address;
        }

        @JsonProperty("full_name")
        public String getName() {
            return name;
        }

        public Address getAddress() {
            return address;
        }

        @JsonIgnore
        public String getSearchKey() {
            return name.toLowerCase();
        }
    }

    @Serdeable
    public record LineItem(String sku, String description, int quantity) {
    }

    @Serdeable
    public record FeatureMessage(String subject, int priority) {
    }

    public enum PurchaseStatus {
        PENDING,
        CONFIRMED
    }

    @Serdeable
    @JsonPropertyOrder({"purchase_id", "buyer", "items", "stock", "status", "comment"})
    public record Purchase(
            @JsonProperty("purchase_id") String id,
            Buyer buyer,
            List<LineItem> items,
            Map<String, Integer> stock,
            PurchaseStatus status,
            @JsonInclude(JsonInclude.Include.NON_NULL) String comment,
            @JsonIgnore String internalCode) {
    }

    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = EmailNotification.class, name = "email"))
    public interface Notification {
    }

    @Serdeable
    public record EmailNotification(String recipient, String subject) implements Notification {
    }

    public interface PublicView {
    }

    public interface InternalView extends PublicView {
    }

    @Serdeable
    public record Account(
            @JsonView(PublicView.class) String username,
            @JsonView(InternalView.class) String accessToken) {
    }

    @Serdeable
    public static final class UserPreferences {

        private String displayName;
        @JsonMerge
        private Map<String, String> settings = new LinkedHashMap<>();

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Map<String, String> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, String> settings) {
            this.settings = settings;
        }
    }
}
