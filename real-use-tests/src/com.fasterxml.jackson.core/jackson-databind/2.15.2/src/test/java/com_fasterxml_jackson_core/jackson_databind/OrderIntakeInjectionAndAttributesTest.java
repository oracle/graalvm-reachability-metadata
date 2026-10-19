/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Value injection and context attributes: `@JacksonInject` creator parameters and injector
 * methods fed from mapper-wide or per-reader `InjectableValues.Std` and shielded from payload
 * values, reader attributes and per-call `DeserializationContext` attributes consumed by a
 * custom deserializer, and writer attributes emitted as virtual properties through `@JsonAppend`.
 *
 * An order-intake service binds orders posted by shop front-ends. The tenant and the receipt
 * clock come from the server, never from the payload; bare line prices are priced in the
 * currency of the shop's storefront; every line gets a sequence number within its order; and
 * each response echoes the request id and the node that served it.
 */
public class OrderIntakeInjectionAndAttributesTest {

    private static final String DEFAULT_CURRENCY = "defaultCurrency";

    private static final Clock MAPPER_CLOCK = Clock.fixed(Instant.parse("2024-03-01T08:00:00Z"), ZoneOffset.UTC);

    private static final Clock REQUEST_CLOCK = Clock.fixed(Instant.parse("2024-03-01T09:15:30Z"), ZoneOffset.UTC);

    private static final String ORDER_JSON = """
            {
              "orderId": "SO-1001",
              "lines": [
                {"sku": "TEA-GREEN", "quantity": 2, "price": 4.5},
                {"sku": "MUG-BLUE", "quantity": 1, "price": "12.00 USD"},
                {"sku": "TEA-BLACK", "quantity": 3, "price": 3.25}
              ]
            }
            """;

    private final ObjectMapper mapper = JsonMapper.builder()
            .injectableValues(new InjectableValues.Std()
                    .addValue("tenant", "shared-shop")
                    .addValue(Clock.class, MAPPER_CLOCK))
            .build();

    @Test
    void injectsServerSideValuesAndPricesBareAmountsInReaderCurrency() throws Exception {
        ObjectReader storefrontReader = mapper.readerFor(Order.class)
                .with(new InjectableValues.Std()
                        .addValue("tenant", "tea-house")
                        .addValue(Clock.class, REQUEST_CLOCK))
                .withAttribute(DEFAULT_CURRENCY, "EUR");

        Order order = storefrontReader.readValue(ORDER_JSON);

        assertEquals("SO-1001", order.getOrderId());
        assertEquals("tea-house", order.getTenant());
        assertEquals("2024-03-01T09:15:30Z", order.getReceivedAt());
        assertEquals(List.of(1, 2, 3), order.getLines().stream().map(OrderLine::getLineNo).toList());
        assertEquals(List.of("4.5 EUR", "12.00 USD", "3.25 EUR"),
                order.getLines().stream().map(OrderLine::getPrice).toList());
    }

    @Test
    void fallsBackToMapperInjectablesAndRestartsLineNumbersPerCall() throws Exception {
        ObjectReader reader = mapper.readerFor(Order.class).withAttribute(DEFAULT_CURRENCY, "GBP");

        Order first = reader.readValue(ORDER_JSON);
        Order second = reader.readValue("""
                {"orderId": "SO-1002", "lines": [{"sku": "TEA-WHITE", "quantity": 5, "price": 7}]}
                """);

        assertEquals("shared-shop", first.getTenant());
        assertEquals("2024-03-01T08:00:00Z", first.getReceivedAt());
        assertEquals(3, first.getLines().get(2).getLineNo());
        assertEquals("SO-1002", second.getOrderId());
        assertEquals(1, second.getLines().get(0).getLineNo());
        assertEquals("7 GBP", second.getLines().get(0).getPrice());
    }

    @Test
    void payloadCannotOverrideInjectedTenant() throws Exception {
        String spoofed = """
                {"orderId": "SO-1003", "tenant": "attacker", "lines": []}
                """;

        Order order = mapper.readerFor(Order.class).readValue(spoofed);

        assertEquals("shared-shop", order.getTenant());
        assertTrue(order.getLines().isEmpty());
    }

    @Test
    void rejectsBareAmountWithoutStorefrontCurrency() {
        ObjectReader reader = mapper.readerFor(Order.class);

        MismatchedInputException error = assertThrows(MismatchedInputException.class,
                () -> reader.readValue(ORDER_JSON));

        assertTrue(error.getMessage().contains("no storefront currency"), error.getMessage());
    }

    @Test
    void reportsMissingInjectableValuesAsDefinitionProblem() {
        ObjectMapper unconfigured = new ObjectMapper();

        InvalidDefinitionException error = assertThrows(InvalidDefinitionException.class,
                () -> unconfigured.readerFor(Order.class)
                        .withAttribute(DEFAULT_CURRENCY, "EUR")
                        .readValue(ORDER_JSON));

        assertTrue(error.getMessage().contains("injectableValues"), error.getMessage());
    }

    @Test
    void appendsWriterAttributesAsVirtualProperties() throws Exception {
        Order order = mapper.readerFor(Order.class).withAttribute(DEFAULT_CURRENCY, "EUR").readValue(ORDER_JSON);
        mapper.setDefaultAttributes(ContextAttributes.getEmpty().withSharedAttribute("servedBy", "intake-eu-1"));
        ObjectWriter requestWriter = mapper.writer().withAttribute("requestId", "req-42");

        JsonNode response = mapper.readTree(requestWriter.writeValueAsString(order));
        JsonNode redirected = mapper.readTree(requestWriter
                .withAttributes(Map.of("servedBy", "intake-us-2"))
                .writeValueAsString(order));
        JsonNode untracked = mapper.readTree(mapper.writer()
                .withoutAttribute("servedBy")
                .writeValueAsString(order));

        assertEquals("req-42", response.get("requestId").asText());
        assertEquals("intake-eu-1", response.get("servedBy").asText());
        assertEquals("shared-shop", response.get("tenant").asText());
        assertEquals("12.00 USD", response.at("/lines/1/price").asText());
        assertEquals("intake-us-2", redirected.get("servedBy").asText());
        assertFalse(untracked.has("requestId"));
        assertFalse(untracked.has("servedBy"));
        assertEquals("SO-1001", untracked.get("orderId").asText());
    }

    /** An order bound from the payload plus values only the server may supply. */
    @JsonIgnoreProperties(value = "tenant", allowGetters = true)
    @JsonAppend(attrs = {@JsonAppend.Attr("requestId"), @JsonAppend.Attr("servedBy")})
    static final class Order {

        private final String orderId;

        private final String tenant;

        private final List<OrderLine> lines;

        private String receivedAt;

        @JsonCreator
        Order(@JsonProperty("orderId") String orderId,
                @JacksonInject("tenant") String tenant,
                @JsonProperty("lines") List<OrderLine> lines) {
            this.orderId = orderId;
            this.tenant = tenant;
            this.lines = lines;
        }

        @JacksonInject
        void stampReceipt(Clock clock) {
            this.receivedAt = clock.instant().toString();
        }

        public String getOrderId() {
            return orderId;
        }

        public String getTenant() {
            return tenant;
        }

        public String getReceivedAt() {
            return receivedAt;
        }

        public List<OrderLine> getLines() {
            return lines;
        }
    }

    /** One order line; its sequence number is assigned while the order is read. */
    @JsonDeserialize(using = OrderLineDeserializer.class)
    static final class OrderLine {

        private final int lineNo;

        private final String sku;

        private final int quantity;

        private final Money price;

        OrderLine(int lineNo, String sku, int quantity, Money price) {
            this.lineNo = lineNo;
            this.sku = sku;
            this.quantity = quantity;
            this.price = price;
        }

        public int getLineNo() {
            return lineNo;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getPrice() {
            return price.toString();
        }
    }

    /** An amount in a currency. */
    record Money(BigDecimal amount, String currency) {

        @Override
        public String toString() {
            return amount.toPlainString() + " " + currency;
        }
    }

    /**
     * Reads a line, numbering it from a per-call counter and pricing bare amounts in the
     * reader's storefront currency.
     */
    static final class OrderLineDeserializer extends StdDeserializer<OrderLine> {

        private static final String LINE_COUNTER = "lineCounter";

        OrderLineDeserializer() {
            super(OrderLine.class);
        }

        @Override
        public OrderLine deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.readValueAsTree();
            Integer previous = (Integer) context.getAttribute(LINE_COUNTER);
            int lineNo = previous == null ? 1 : previous + 1;
            context.setAttribute(LINE_COUNTER, lineNo);
            return new OrderLine(lineNo, node.get("sku").asText(), node.get("quantity").asInt(),
                    readPrice(node.get("price"), context));
        }

        private Money readPrice(JsonNode price, DeserializationContext context) throws IOException {
            if (price.isNumber()) {
                String currency = (String) context.getAttribute(DEFAULT_CURRENCY);
                if (currency == null) {
                    return context.reportInputMismatch(OrderLine.class,
                            "Bare amount %s but no storefront currency is configured", price.asText());
                }
                return new Money(price.decimalValue(), currency);
            }
            String[] parts = price.asText().split(" ");
            return new Money(new BigDecimal(parts[0]), parts[1]);
        }
    }
}
