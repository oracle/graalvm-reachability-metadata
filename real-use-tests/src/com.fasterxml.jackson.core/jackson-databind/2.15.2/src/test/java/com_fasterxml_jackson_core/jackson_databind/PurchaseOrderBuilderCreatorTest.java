/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Builder and creator based deserialization of immutable types: `@JsonDeserialize(builder = ...)`
 * with a `@JsonPOJOBuilder` that has custom prefixes, delegating and property-based `@JsonCreator`
 * factory methods, and failure on missing or null creator properties.
 *
 * An ordering service accepts purchase orders from partners. Orders are immutable and assembled
 * through a builder, SKUs travel as plain strings and are normalised by a factory method, and
 * order lines are created by a validating factory that refuses non-positive quantities.
 */
public class PurchaseOrderBuilderCreatorTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();

    @Test
    void readsOrderThroughBuilderAndFactoryMethods() throws Exception {
        String json = """
                {
                  "orderId": "PO-1001",
                  "customer": "acme",
                  "line": [
                    {"sku": " ab-123 ", "quantity": 3},
                    {"sku": "cd-9", "quantity": 1}
                  ],
                  "priority": true
                }
                """;

        PurchaseOrder order = mapper.readValue(json, PurchaseOrder.class);

        assertEquals("PO-1001", order.orderId());
        assertEquals("ACME", order.customer());
        assertTrue(order.priority());
        assertEquals(List.of(new OrderLine(Sku.of("AB-123"), 3), new OrderLine(Sku.of("CD-9"), 1)),
                order.lines());
        assertEquals("AB-123", order.lines().get(0).sku().code());
    }

    @Test
    void builderDefaultsApplyWhenPropertiesAreAbsent() throws Exception {
        String json = """
                {"orderId": "PO-1002", "customer": "globex", "line": [{"sku": "ef-1", "quantity": 2}]}
                """;

        PurchaseOrder order = mapper.readValue(json, PurchaseOrder.class);

        assertEquals("GLOBEX", order.customer());
        assertEquals(false, order.priority());
        assertEquals(1, order.lines().size());
    }

    @Test
    void writesOrderAndReadsItBackUnchanged() throws Exception {
        PurchaseOrder order = new PurchaseOrder.Builder()
                .withOrderId("PO-1003")
                .withCustomer("initech")
                .addLine(new OrderLine(Sku.of("gh-7"), 5))
                .withPriority(true)
                .create();

        String json = mapper.writeValueAsString(order);
        JsonNode tree = mapper.readTree(json);

        assertEquals("GH-7", tree.get("line").get(0).get("sku").asText());
        assertEquals(5, tree.get("line").get(0).get("quantity").asInt());
        assertEquals(order, mapper.readValue(json, PurchaseOrder.class));
    }

    @Test
    void reportsMissingCreatorProperty() {
        String json = """
                {"sku": "ab-123"}
                """;

        MismatchedInputException error = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(json, OrderLine.class));

        assertTrue(error.getOriginalMessage().contains("quantity"), error.getOriginalMessage());
    }

    @Test
    void reportsNullCreatorProperty() {
        String json = """
                {"sku": null, "quantity": 2}
                """;

        MismatchedInputException error = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(json, OrderLine.class));

        assertTrue(error.getOriginalMessage().contains("sku"), error.getOriginalMessage());
    }

    @Test
    void wrapsValidationFailureFromFactoryMethod() {
        String json = """
                {"orderId": "PO-1004", "customer": "acme", "line": [{"sku": "ab-1", "quantity": 0}]}
                """;

        ValueInstantiationException error = assertThrows(ValueInstantiationException.class,
                () -> mapper.readValue(json, PurchaseOrder.class));

        assertEquals(OrderLine.class, error.getType().getRawClass());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertEquals("quantity must be positive: 0", error.getCause().getMessage());
    }

    @Test
    void rejectsOrderWhoseBuilderRefusesToBuild() {
        String json = """
                {"orderId": "PO-1005", "customer": "acme"}
                """;

        ValueInstantiationException error = assertThrows(ValueInstantiationException.class,
                () -> mapper.readValue(json, PurchaseOrder.class));

        assertEquals("order PO-1005 has no lines", error.getCause().getMessage());
    }

    /// An immutable order that partners submit; only the builder can create it.
    @JsonDeserialize(builder = PurchaseOrder.Builder.class)
    public static final class PurchaseOrder {

        private final String orderId;
        private final String customer;
        private final List<OrderLine> lines;
        private final boolean priority;

        private PurchaseOrder(Builder builder) {
            this.orderId = builder.orderId;
            this.customer = builder.customer;
            this.lines = List.copyOf(builder.lines);
            this.priority = builder.priority;
        }

        @JsonProperty("orderId")
        public String orderId() {
            return orderId;
        }

        @JsonProperty("customer")
        public String customer() {
            return customer;
        }

        @JsonProperty("line")
        public List<OrderLine> lines() {
            return lines;
        }

        @JsonProperty("priority")
        public boolean priority() {
            return priority;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PurchaseOrder that
                    && orderId.equals(that.orderId)
                    && customer.equals(that.customer)
                    && lines.equals(that.lines)
                    && priority == that.priority;
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, customer, lines, priority);
        }

        /// Collects order properties one at a time; `addLine` is for application code only.
        @JsonPOJOBuilder(withPrefix = "with", buildMethodName = "create")
        public static final class Builder {

            private String orderId;
            private String customer;
            private final List<OrderLine> lines = new ArrayList<>();
            private boolean priority;

            public Builder withOrderId(String orderId) {
                this.orderId = orderId;
                return this;
            }

            public Builder withCustomer(String customer) {
                this.customer = customer.toUpperCase();
                return this;
            }

            @JsonProperty("line")
            public Builder withLines(List<OrderLine> lines) {
                this.lines.addAll(lines);
                return this;
            }

            public Builder addLine(OrderLine line) {
                this.lines.add(line);
                return this;
            }

            public Builder withPriority(boolean priority) {
                this.priority = priority;
                return this;
            }

            public PurchaseOrder create() {
                if (lines.isEmpty()) {
                    throw new IllegalArgumentException("order " + orderId + " has no lines");
                }
                return new PurchaseOrder(this);
            }
        }
    }

    /// One ordered product, created through a validating property-based factory.
    public static final class OrderLine {

        private final Sku sku;
        private final int quantity;

        private OrderLine(Sku sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        @JsonCreator
        public static OrderLine of(@JsonProperty("sku") Sku sku, @JsonProperty("quantity") int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
            return new OrderLine(sku, quantity);
        }

        @JsonProperty("sku")
        public Sku sku() {
            return sku;
        }

        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OrderLine that && sku.equals(that.sku) && quantity == that.quantity;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku, quantity);
        }
    }

    /// A stock keeping unit that travels as a bare string and is stored trimmed and upper-case.
    public static final class Sku {

        private final String code;

        private Sku(String code) {
            this.code = code;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Sku of(String raw) {
            return new Sku(raw.trim().toUpperCase());
        }

        @JsonValue
        public String code() {
            return code;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Sku that && code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }
    }
}
