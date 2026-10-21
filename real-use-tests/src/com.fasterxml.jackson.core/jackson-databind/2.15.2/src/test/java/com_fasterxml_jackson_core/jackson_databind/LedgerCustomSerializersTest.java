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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Custom serializers and deserializers: value and map-key handlers registered through a
 * `SimpleModule`, and per-property handlers selected with `@JsonSerialize(using = ...)` and
 * `@JsonDeserialize(using = ...)`.
 *
 * A bookkeeping service exchanges ledger entries whose amounts travel as compact strings such
 * as `"12.50 EUR"`, keeps account balances in a map keyed by IBAN, masks the counterparty's
 * card number on the way out, and rejects amounts that do not follow the agreed format.
 */
public class LedgerCustomSerializersTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new LedgerModule())
            .build();

    @Test
    void writesMoneyAndIbanThroughModuleHandlers() throws Exception {
        Statement statement = new Statement(new Iban("NL91ABNA0417164300"),
                new Money(new BigDecimal("1250.00"), "EUR"),
                List.of(new Entry("rent", new Money(new BigDecimal("-950.00"), "EUR"), "4111111111111111")));

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(statement));

        assertEquals("NL91 ABNA 0417 1643 00", tree.get("account").asText());
        assertEquals("1250.00 EUR", tree.get("balance").asText());
        assertEquals("-950.00 EUR", tree.get("entries").get(0).get("amount").asText());
        assertEquals("************1111", tree.get("entries").get(0).get("card").asText());
    }

    @Test
    void readsMoneyFromStringAndFromObjectForm() throws Exception {
        String json = """
                {
                  "account": "DE89 3704 0044 0532 0130 00",
                  "balance": "310.25 EUR",
                  "entries": [
                    {"memo": "salary", "amount": {"value": 2500, "currency": "EUR"}, "card": "5500000000000004"},
                    {"memo": "groceries", "amount": "-84.10 EUR", "card": "5500000000000004"}
                  ]
                }
                """;

        Statement statement = mapper.readValue(json, Statement.class);

        assertEquals("DE89370400440532013000", statement.account().compact());
        assertEquals(new Money(new BigDecimal("310.25"), "EUR"), statement.balance());
        assertEquals(new Money(new BigDecimal("2500"), "EUR"), statement.entries().get(0).amount());
        assertEquals(new Money(new BigDecimal("-84.10"), "EUR"), statement.entries().get(1).amount());
        assertEquals("salary", statement.entries().get(0).memo());
        assertEquals("5500000000000004", statement.entries().get(1).card());
    }

    @Test
    void keysBalancesByIbanInBothDirections() throws Exception {
        Map<Iban, Money> balances = new LinkedHashMap<>();
        balances.put(new Iban("NL91ABNA0417164300"), new Money(new BigDecimal("10.00"), "EUR"));
        balances.put(new Iban("GB29NWBK60161331926819"), new Money(new BigDecimal("7.50"), "GBP"));
        Branch branch = new Branch("Amsterdam", balances);

        String json = mapper.writeValueAsString(branch);

        assertEquals("""
                {"name":"Amsterdam","balances":{"NL91 ABNA 0417 1643 00":"10.00 EUR",\
                "GB29 NWBK 6016 1331 9268 19":"7.50 GBP"}}""", json);
        Branch restored = mapper.readValue(json, Branch.class);
        assertEquals(branch, restored);
        assertEquals(new Money(new BigDecimal("7.50"), "GBP"),
                restored.balances().get(new Iban("GB29NWBK60161331926819")));
    }

    @Test
    void rejectsMalformedAmountWithLocation() {
        String json = """
                {"memo": "refund", "amount": "twelve euros", "card": "4111111111111111"}
                """;

        InvalidFormatException error = assertThrows(InvalidFormatException.class,
                () -> mapper.readValue(json, Entry.class));

        assertEquals("twelve euros", error.getValue());
        assertEquals(Money.class, error.getTargetType());
        assertEquals("amount", error.getPath().get(0).getFieldName());
        assertTrue(error.getOriginalMessage().contains("<amount> <currency>"));
    }

    @Test
    void plainMapperWithoutModuleFallsBackToBeanForm() throws Exception {
        ObjectMapper plain = JsonMapper.builder().build();
        Entry entry = new Entry("coffee", new Money(new BigDecimal("-3.20"), "EUR"), "4111111111111111");

        JsonNode tree = plain.valueToTree(entry);

        assertEquals("EUR", tree.get("amount").get("currency").asText());
        assertEquals(0, new BigDecimal("-3.20").compareTo(tree.get("amount").get("value").decimalValue()));
        assertEquals("************1111", tree.get("card").asText());
    }

    /// Registers the money and IBAN handlers the whole service shares.
    public static final class LedgerModule extends SimpleModule {

        public LedgerModule() {
            super("ledger");
            addSerializer(Money.class, new MoneySerializer());
            addDeserializer(Money.class, new MoneyDeserializer());
            addSerializer(Iban.class, new IbanSerializer());
            addDeserializer(Iban.class, new IbanDeserializer());
            addKeySerializer(Iban.class, new IbanKeySerializer());
            addKeyDeserializer(Iban.class, new IbanKeyDeserializer());
        }
    }

    public record Money(BigDecimal value, String currency) {
    }

    /// An account number stored without spaces and printed in groups of four.
    public record Iban(String compact) {

        public Iban {
            compact = compact.replace(" ", "");
        }

        String grouped() {
            StringBuilder grouped = new StringBuilder();
            for (int i = 0; i < compact.length(); i += 4) {
                if (i > 0) {
                    grouped.append(' ');
                }
                grouped.append(compact, i, Math.min(i + 4, compact.length()));
            }
            return grouped.toString();
        }
    }

    public record Statement(Iban account, Money balance, List<Entry> entries) {
    }

    public record Entry(
            String memo,
            Money amount,
            @JsonSerialize(using = CardMaskingSerializer.class)
            @JsonDeserialize(using = CardNumberDeserializer.class)
            String card) {
    }

    public record Branch(String name, Map<Iban, Money> balances) {
    }

    public static final class MoneySerializer extends StdSerializer<Money> {

        public MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money money, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(money.value().toPlainString() + " " + money.currency());
        }
    }

    /// Accepts `"12.50 EUR"` and the older `{"value": 12.50, "currency": "EUR"}` form.
    public static final class MoneyDeserializer extends StdDeserializer<Money> {

        public MoneyDeserializer() {
            super(Money.class);
        }

        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                JsonNode node = context.readTree(parser);
                return new Money(node.get("value").decimalValue(), node.get("currency").asText());
            }
            String text = parser.getValueAsString();
            String[] parts = text.trim().split(" ");
            if (parts.length != 2 || !parts[1].matches("[A-Z]{3}")) {
                return (Money) context.handleWeirdStringValue(Money.class, text,
                        "expected '<amount> <currency>'");
            }
            try {
                return new Money(new BigDecimal(parts[0]), parts[1]);
            } catch (NumberFormatException e) {
                return (Money) context.handleWeirdStringValue(Money.class, text,
                        "expected '<amount> <currency>'");
            }
        }
    }

    public static final class IbanSerializer extends StdSerializer<Iban> {

        public IbanSerializer() {
            super(Iban.class);
        }

        @Override
        public void serialize(Iban iban, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(iban.grouped());
        }
    }

    public static final class IbanDeserializer extends StdDeserializer<Iban> {

        public IbanDeserializer() {
            super(Iban.class);
        }

        @Override
        public Iban deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return new Iban(parser.getValueAsString());
        }
    }

    public static final class IbanKeySerializer extends JsonSerializer<Iban> {

        @Override
        public void serialize(Iban iban, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeFieldName(iban.grouped());
        }
    }

    public static final class IbanKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext context) {
            return new Iban(key);
        }
    }

    /// Writes only the last four digits of a card number.
    public static final class CardMaskingSerializer extends StdSerializer<String> {

        public CardMaskingSerializer() {
            super(String.class);
        }

        @Override
        public void serialize(String card, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString("*".repeat(card.length() - 4) + card.substring(card.length() - 4));
        }
    }

    /// Strips the spaces and dashes people type into card numbers.
    public static final class CardNumberDeserializer extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parser.getValueAsString().replaceAll("[ -]", "");
        }
    }
}
